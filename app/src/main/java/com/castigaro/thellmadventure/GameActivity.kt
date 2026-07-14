package com.castigaro.thellmadventure

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.castigaro.thellmadventure.api.CraftApi
import com.castigaro.thellmadventure.api.DialogApi
import com.castigaro.thellmadventure.api.LlmClient
import com.castigaro.thellmadventure.api.MutationApi
import com.castigaro.thellmadventure.api.RoomGenApi
import com.castigaro.thellmadventure.audio.MusicDirector
import com.castigaro.thellmadventure.audio.Sfx
import com.castigaro.thellmadventure.data.AppSettings
import com.castigaro.thellmadventure.data.DialogMessage
import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.data.SaveStore
import com.castigaro.thellmadventure.databinding.ActivityGameBinding
import com.castigaro.thellmadventure.game.Effect
import com.castigaro.thellmadventure.game.GameEngine
import com.castigaro.thellmadventure.game.GoalChecker
import com.castigaro.thellmadventure.render.IsoView
import com.castigaro.thellmadventure.ui.CraftingController
import com.castigaro.thellmadventure.ui.InventoryBarAdapter
import com.castigaro.thellmadventure.ui.InventoryListAdapter
import com.castigaro.thellmadventure.ui.InventoryRow
import com.castigaro.thellmadventure.world.DemoWorld
import com.castigaro.thellmadventure.world.Door
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.Limits
import com.castigaro.thellmadventure.world.Npc
import com.castigaro.thellmadventure.world.Prop
import com.castigaro.thellmadventure.world.Room
import com.castigaro.thellmadventure.world.Wall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Der Spielbildschirm: isometrische Spielfläche mit Kamera, HUD, Inventar
 * und In-Game-Menü. Bewusst getrennt vom App-Menü (Provider-/API-Key-
 * Einstellungen sind hier NICHT erreichbar — nur über den Hauptbildschirm).
 *
 * Autosave nach jeder zustandsändernden Aktion (Kap-Krakenzahn-Muster):
 * Raumwechsel, Aufnehmen, Menü-Aktionen, onPause.
 */
class GameActivity : AppCompatActivity(), IsoView.Listener {

    private lateinit var binding: ActivityGameBinding
    private lateinit var save: GameSave

    private var activeItemId: String? = null
    private var resumedAt = 0L
    private var busy = false
    private var dialogNpc: Npc? = null

    private lateinit var barAdapter: InventoryBarAdapter
    private lateinit var listAdapter: InventoryListAdapter
    private lateinit var crafting: CraftingController
    private lateinit var music: MusicDirector

    private val hideComment = Runnable { binding.commentBar.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val saveId = intent.getStringExtra(EXTRA_SAVE_ID)
        save = saveId?.let { SaveStore.get(this, it) } ?: DemoWorld.newSave(
            getString(R.string.save_default_name)
        ).also { SaveStore.add(this, it) }

        binding.isoView.listener = this
        binding.isoView.playerLook = save.playerLook
        binding.isoView.npcProvider = { npcId -> save.world.npcs[npcId] }

        barAdapter = InventoryBarAdapter(::onInventoryTap)
        binding.inventoryBar.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.inventoryBar.adapter = barAdapter

        listAdapter = InventoryListAdapter(::onInventoryTap)
        binding.inventoryList.layoutManager = LinearLayoutManager(this)
        binding.inventoryList.adapter = listAdapter

        binding.buttonMenu.setOnClickListener { showMenu() }
        binding.buttonRecenter.setOnClickListener { binding.isoView.recenterCamera() }
        binding.buttonInventoryToggle.setOnClickListener { toggleInventoryPanel() }
        binding.buttonCraft.setOnClickListener { crafting.open() }
        binding.dialogOverlay.dialogClose.setOnClickListener { closeDialog() }

        crafting = CraftingController(
            binding = binding.craftOverlay,
            inventory = { save.world.inventory() },
            onCombine = ::onCraftCombine,
        )
        music = MusicDirector(this)

        // Zurück-Taste schließt zuerst offene Overlays.
        onBackPressedDispatcher.addCallback(this) {
            when {
                binding.craftOverlay.root.visibility == View.VISIBLE -> crafting.close()
                binding.dialogOverlay.dialogRoot.visibility == View.VISIBLE -> closeDialog()
                binding.inventoryPanel.visibility == View.VISIBLE -> toggleInventoryPanel()
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        enterRoom(save.roomId, save.ptx to save.pty)
        refreshInventory()
    }

    override fun onResume() {
        super.onResume()
        resumedAt = System.currentTimeMillis()
        binding.isoView.animate = true
        applyFullscreen(AppSettings.isFullscreen(this))
        playRoomMusic()
    }

    override fun onPause() {
        super.onPause()
        binding.isoView.animate = false
        music.stop()
        save.playtimeMs += System.currentTimeMillis() - resumedAt
        autosave()
    }

    /** Spielt die Melodie des aktuellen Raums (manifestierte KI-Melodie oder eingebaute). */
    private fun playRoomMusic() {
        val room = save.world.room(save.roomId) ?: return
        val spec = room.melodyId?.let { save.world.melodies[it] }
        music.switchTo(room.id, spec)
    }

    // ---- Raum & Bewegung -----------------------------------------------------

    private fun enterRoom(roomId: String, tile: Pair<Int, Int>) {
        val room = save.world.room(roomId) ?: return
        save.roomId = roomId
        save.ptx = tile.first
        save.pty = tile.second
        binding.isoView.bind(room, tile)
        binding.roomLabel.text = room.name
        playRoomMusic()
    }

    override fun onDoorReached(door: Door) {
        rememberPlayerTile()
        val target = door.targetRoomId
        when {
            door.locked -> comment(door.lockHint ?: getString(R.string.door_locked))
            target == null -> generateRoom(door)
            else -> {
                val targetRoom = save.world.room(target) ?: return
                val entry = entryTile(targetRoom, from = save.roomId)
                Sfx.play(this, Sfx.DOOR)
                enterRoom(target, entry)
                comment(getString(R.string.room_entered, targetRoom.name))
                autosave()
            }
        }
    }

    /** Unentdeckte Tür → Raum von der KI erzeugen lassen (mit Lade-Overlay). */
    private fun generateRoom(door: Door) {
        if (busy) return
        val origin = save.world.room(save.roomId) ?: return
        setLoading(true, getString(R.string.room_generating))
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    RoomGenApi.generate(this@GameActivity, save, origin, door)
                }
            }
            setLoading(false, "")
            result.onSuccess { room ->
                val entry = entryTile(room, from = origin.id)
                Sfx.play(this@GameActivity, Sfx.DOOR)
                enterRoom(room.id, entry)
                if (!save.growthHintShown && save.world.rooms.size >= Limits.MAX_RAEUME) {
                    // Kein Hard-Cap: einmal pro Spielstand darauf hinweisen,
                    // dass weitere Räume die KI-Anfragen wachsen lassen.
                    save.growthHintShown = true
                    comment(getString(R.string.world_growth_hint))
                } else {
                    comment(getString(R.string.room_entered, room.name))
                }
                autosave()
            }.onFailure { error ->
                // Kein Offline-Inhalt: Tür „klemmt" und bleibt erneut versuchbar.
                comment(getString(R.string.door_stuck))
                (error as? LlmClient.ApiException)?.message?.let {
                    android.widget.Toast.makeText(this@GameActivity, it, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLoading(loading: Boolean, text: String) {
        busy = loading
        binding.loadingOverlay.root.visibility = if (loading) View.VISIBLE else View.GONE
        binding.loadingOverlay.loadingText.text = text
    }

    /** Eintrittskachel im Zielraum: eine Kachel einwärts der Rücktür. */
    private fun entryTile(targetRoom: Room, from: String): Pair<Int, Int> {
        val backDoor = targetRoom.doors.firstOrNull { it.targetRoomId == from }
            ?: return targetRoom.w / 2 to targetRoom.h / 2
        val (tx, ty) = backDoor.tile(targetRoom.w, targetRoom.h)
        val inward = when (backDoor.wall) {
            Wall.N -> tx to ty + 1
            Wall.S -> tx to ty - 1
            Wall.W -> tx + 1 to ty
            Wall.O -> tx - 1 to ty
        }
        return if (targetRoom.walkable(inward.first, inward.second)) inward else tx to ty
    }

    override fun onPropReached(prop: Prop) {
        rememberPlayerTile()
        val active = activeItemId?.let { save.world.items[it] }

        // 1) „Benutze [Gegenstand] mit [Requisite]" → KI-Schiedsrichter
        if (active != null && active.holder == Item.HOLDER_INVENTORY) {
            runMutation(prop, active)
            return
        }
        // 2) Offen liegender Gegenstand → aufnehmen (verschwindet aus dem Raum)
        val itemId = prop.itemId
        if (itemId != null && prop.states["verschlossen"] != true) {
            val item = save.world.items[itemId]
            GameEngine.apply(save, listOf(Effect.TakeItem(itemId)))
            Sfx.play(this, Sfx.PICKUP)
            comment(getString(R.string.item_taken, item?.name ?: itemId))
            refreshInventory()
            autosave()
            checkGoal()
            return
        }
        // 3) Sonst: die Requisite ohne Gegenstand benutzen → KI-Schiedsrichter
        runMutation(prop, null)
    }

    private fun runMutation(prop: Prop, withItem: Item?) {
        if (busy) return
        val room = save.world.room(save.roomId) ?: return
        // Cache-Treffer laufen sofort und offline (keine Effekte mehr).
        setLoading(true, getString(R.string.room_generating))
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    MutationApi.interact(this@GameActivity, save, room, prop, withItem)
                }
            }
            setLoading(false, "")
            result.onSuccess { res ->
                if (!res.gecacht) GameEngine.apply(save, res.effekte)
                comment(res.text)
                activeItemId = null
                refreshInventory()
                autosave()
                checkGoal()
            }.onFailure {
                comment(getString(R.string.use_nothing_happens))
            }
        }
    }

    override fun onNpcReached(npc: Npc) {
        rememberPlayerTile()
        openDialog(npc)
    }

    override fun onWalkOnly() {
        rememberPlayerTile()
    }

    override fun onBlockedTarget() {
        comment(getString(R.string.walk_blocked))
    }

    override fun onPanModeChanged(panning: Boolean) {
        binding.buttonRecenter.visibility = if (panning) View.VISIBLE else View.GONE
    }

    private fun rememberPlayerTile() {
        val (tx, ty) = binding.isoView.playerTile()
        save.ptx = tx
        save.pty = ty
    }

    // ---- Inventar --------------------------------------------------------------

    private fun onInventoryTap(item: Item) {
        activeItemId = if (activeItemId == item.id) null else item.id
        refreshInventory()
        comment(
            if (activeItemId != null) getString(R.string.use_needs_target, item.name)
            else getString(R.string.item_deselected)
        )
    }

    private fun refreshInventory() {
        val inventory = save.world.inventory()
        barAdapter.submit(inventory, activeItemId)
        binding.buttonCraft.isEnabled = inventory.size >= 2

        val rows = mutableListOf<InventoryRow>()
        rows.add(
            InventoryRow.Header(
                if (inventory.isEmpty()) getString(R.string.inventory_empty)
                else getString(R.string.inventory_items)
            )
        )
        inventory.forEach { rows.add(InventoryRow.ItemRow(it)) }
        if (save.world.skills.isNotEmpty()) {
            rows.add(InventoryRow.Header(getString(R.string.inventory_skills)))
            save.world.skills.values.forEach { rows.add(InventoryRow.SkillRow(it)) }
        }
        listAdapter.submit(rows, activeItemId)
    }

    private fun toggleInventoryPanel() {
        val open = binding.inventoryPanel.visibility == View.VISIBLE
        binding.inventoryPanel.visibility = if (open) View.GONE else View.VISIBLE
        binding.buttonInventoryToggle.text =
            getString(if (open) R.string.inventory_expand else R.string.inventory_collapse)
    }

    // ---- HUD ---------------------------------------------------------------

    private fun comment(text: String) {
        binding.commentBar.text = text
        binding.commentBar.visibility = View.VISIBLE
        binding.commentBar.removeCallbacks(hideComment)
        binding.commentBar.postDelayed(hideComment, 3500L)
    }

    // ---- In-Game-Menü (getrennt vom App-Menü) --------------------------------

    private fun showMenu() {
        val fullscreen = AppSettings.isFullscreen(this)
        val optionen = arrayOf(
            getString(R.string.game_save),
            getString(R.string.game_save_as_story),
            getString(R.string.game_settings),
            getString(if (fullscreen) R.string.menu_fullscreen_off else R.string.menu_fullscreen_on),
            getString(R.string.game_quit),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.game_menu_title)
            .setItems(optionen) { _, which ->
                when (which) {
                    0 -> saveNamedSlot()
                    1 -> saveAsStory()
                    2 -> startActivity(android.content.Intent(this, GameSettingsActivity::class.java))
                    3 -> {
                        AppSettings.setFullscreen(this, !fullscreen)
                        applyFullscreen(!fullscreen)
                    }
                    4 -> confirmQuit()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** „Als neue Geschichte sichern": Schnappschuss der Welt als Startvorlage. */
    private fun saveAsStory() {
        rememberPlayerTile()
        autosave()
        val input = android.widget.EditText(this).apply {
            setText(save.world.story?.titel ?: save.name)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.game_save_as_story_title)
            .setMessage(R.string.game_save_as_story_message)
            .setView(input)
            .setPositiveButton(R.string.game_save_as_story_confirm) { _, _ ->
                val name = input.text.toString().ifBlank { save.name }
                com.castigaro.thellmadventure.data.StoryTemplates.saveFrom(this, save, name)
                comment(getString(R.string.game_saved_as_story))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveNamedSlot() {
        rememberPlayerTile()
        autosave()
        val stamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
        val clone = save.cloneAs(getString(R.string.save_clone_name, stamp))
        SaveStore.add(this, clone)
        comment(getString(R.string.game_saved))
    }

    private fun confirmQuit() {
        AlertDialog.Builder(this)
            .setTitle(R.string.game_quit_title)
            .setMessage(R.string.game_quit_message)
            .setPositiveButton(R.string.game_quit) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun autosave() {
        rememberPlayerTile()
        SaveStore.update(this, save)
    }

    // ---- Dialog (Optionen-basiert) -------------------------------------------

    private fun openDialog(npc: Npc) {
        if (busy) return
        dialogNpc = npc
        activeItemId = null
        binding.dialogOverlay.dialogRoot.visibility = View.VISIBLE
        binding.dialogOverlay.dialogNpcName.text = npc.name
        binding.dialogOverlay.dialogSpeech.text = ""
        binding.dialogOverlay.dialogOptions.removeAllViews()
        requestDialogTurn(npc, chosen = null)
    }

    private fun requestDialogTurn(npc: Npc, chosen: String?) {
        setDialogLoading(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { DialogApi.turn(this@GameActivity, save, npc, chosen) }
            }
            setDialogLoading(false)
            result.onSuccess { turn ->
                // Verlauf fortschreiben: gewählte Antwort + NPC-Rede
                chosen?.let { save.historyFor(npc.id).add(DialogMessage(DialogMessage.ROLE_PLAYER, it)) }
                save.historyFor(npc.id).add(DialogMessage(DialogMessage.ROLE_NPC, turn.sag))
                GameEngine.apply(save, turn.effekte)
                showDialogTurn(npc, turn)
                refreshInventory()
                autosave()
                checkGoal()
            }.onFailure {
                // Kein Offline-Dialog: die Figur ist „beschäftigt".
                comment(getString(R.string.dialog_busy, npc.name))
                closeDialog()
                (it as? LlmClient.ApiException)?.message?.let { msg ->
                    android.widget.Toast.makeText(this@GameActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showDialogTurn(npc: Npc, turn: DialogApi.Turn) {
        if (save.finished) return // Ende-Dialog übernimmt
        binding.dialogOverlay.dialogSpeech.text = turn.sag
        binding.dialogOverlay.dialogOptions.removeAllViews()
        turn.optionen.forEach { option ->
            val button = com.google.android.material.button.MaterialButton(
                this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            ).apply {
                text = option
                isAllCaps = false
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.option_gap) }
                setOnClickListener { requestDialogTurn(npc, option) }
            }
            binding.dialogOverlay.dialogOptions.addView(button)
        }
    }

    private fun setDialogLoading(loading: Boolean) {
        binding.dialogOverlay.dialogSpinner.visibility = if (loading) View.VISIBLE else View.GONE
        for (i in 0 until binding.dialogOverlay.dialogOptions.childCount) {
            binding.dialogOverlay.dialogOptions.getChildAt(i).isEnabled = !loading
        }
    }

    private fun closeDialog() {
        dialogNpc = null
        binding.dialogOverlay.dialogRoot.visibility = View.GONE
    }

    // ---- Crafting ------------------------------------------------------------

    private fun onCraftCombine(inputIds: List<String>) {
        if (busy) return
        setLoading(true, getString(R.string.room_generating))
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { CraftApi.craft(this@GameActivity, save, inputIds) }
            }
            setLoading(false, "")
            result.onSuccess { res ->
                comment(res.text)
                if (res.erfolg) {
                    Sfx.play(this@GameActivity, Sfx.CRAFT)
                    crafting.close()
                }
                crafting.reset()
                refreshInventory()
                crafting.refreshInventory()
                autosave()
                checkGoal()
            }.onFailure {
                comment(getString(R.string.craft_slipped))
            }
        }
    }

    // ---- Ziel & Ende ---------------------------------------------------------

    /** Prüft nach jeder Zustandsänderung, ob das geheime Ziel erreicht ist. */
    private fun checkGoal() {
        if (GoalChecker.isReached(save)) {
            GameEngine.apply(save, listOf(Effect.EndGame))
            autosave()
            showEnding()
        }
    }

    private fun showEnding() {
        Sfx.play(this, Sfx.SUCCESS)
        val world = save.world
        val ziel = world.story?.geheimesZiel?.umschreibung.orEmpty()
        val minuten = (save.playtimeMs + (System.currentTimeMillis() - resumedAt)) / 60000
        val message = getString(
            R.string.ending_message,
            world.story?.titel.orEmpty(),
            ziel,
            world.rooms.size,
            minuten,
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.ending_title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.ending_back) { _, _ -> finish() }
            .show()
    }

    // ---- Vollbild ------------------------------------------------------------

    private fun applyFullscreen(enabled: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        const val EXTRA_SAVE_ID = "saveId"
    }
}
