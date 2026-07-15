package com.castigaro.thellmadventure.world

import com.castigaro.thellmadventure.data.GameSave

/**
 * Hartkodierte 2-Raum-Demo-Welt für die Aufbauphasen 1–2. Ab Phase 3
 * erschafft die KI die Räume — diese Welt bleibt als Spielwiese für
 * Renderer, Persistenz und Inventar (Raum 1 ist bewusst 14×10 Kacheln:
 * größer als jedes Display, damit die Kamera scrollen muss).
 */
object DemoWorld {

    fun newSave(name: String): GameSave {
        val world = WorldState()

        val r1 = Room(
            id = world.nextId("r"),
            name = "Verlassene Halle",
            w = 14,
            h = 10,
            palette = Palette.STAHL,
        )
        val r2 = Room(
            id = world.nextId("r"),
            name = "Wartungsgang",
            w = 8,
            h = 5,
            palette = Palette.ROST,
        )

        // Türen: r1 ↔ r2 über die Südwand; zwei unentdeckte "?"-Türen.
        r1.doors.add(Door(Wall.N, 4, targetRoomId = null, label = "Schleuse"))
        r1.doors.add(Door(Wall.O, 5, targetRoomId = null, locked = true, lockHint = "Der Kartenleser blinkt rot.", label = "Ostluke"))
        r1.doors.add(Door(Wall.S, 9, targetRoomId = r2.id, label = "Wartungsgang"))
        r2.doors.add(Door(Wall.N, 2, targetRoomId = r1.id, label = "Halle"))
        r2.doors.add(Door(Wall.O, 2, targetRoomId = null, label = "Kriechschacht"))

        // Gegenstände: liegen in Requisiten, verschwinden beim Aufnehmen.
        val i1 = Item(
            world.nextId("i"), "Alter Schlüssel", "Kalt, schwer, dreizackiger Bart.",
            IconSpec(colorIdx = 3, motiv = IconArchetype.SCHLUESSEL, material = IconMaterial.MESSING, abgenutzt = true),
        )
        val i2 = Item(
            world.nextId("i"), "Brecheisen", "Gutes, ehrliches Werkzeug.",
            IconSpec(colorIdx = 7, motiv = IconArchetype.WERKZEUG, material = IconMaterial.EISEN),
        )
        val i3 = Item(
            world.nextId("i"), "Ölkanne", "Halb voll. Oder halb leer.",
            IconSpec(colorIdx = 6, motiv = IconArchetype.KRUG, material = IconMaterial.EISEN),
        )

        r1.props.addAll(
            listOf(
                Prop(world.nextId("p"), PropType.TRUHE, 3, 2, "Alte Truhe", colorSlot = 6, states = mutableMapOf("verschlossen" to true), itemId = i1.id),
                Prop(world.nextId("p"), PropType.TERMINAL, 8, 1, "Flackerndes Terminal", colorSlot = 7, states = mutableMapOf("an" to true)),
                Prop(world.nextId("p"), PropType.FASS, 11, 3, "Rostiges Fass", colorSlot = 6),
                Prop(world.nextId("p"), PropType.FASS, 12, 3, "Rostiges Fass", colorSlot = 6),
                Prop(world.nextId("p"), PropType.PFLANZE, 1, 7, "Zähe Topfpflanze", colorSlot = 2),
                Prop(world.nextId("p"), PropType.LAMPE, 6, 5, "Flurlampe", colorSlot = 7, blocksTile = false),
                Prop(world.nextId("p"), PropType.TEPPICH, 7, 7, "Abgetretener Läufer", colorSlot = 4, blocksTile = false),
                Prop(world.nextId("p"), PropType.KISTE, 4, 8, "Sperrholzkiste", colorSlot = 6, itemId = i2.id),
                Prop(world.nextId("p"), PropType.HEBEL, 13, 8, "Schwerer Hebel", colorSlot = 3),
                Prop(world.nextId("p"), PropType.KRISTALL, 10, 6, "Summender Kristall", colorSlot = 5),
            )
        )
        r2.props.addAll(
            listOf(
                Prop(world.nextId("p"), PropType.FASS, 5, 2, "Ölfass", colorSlot = 6, itemId = i3.id),
                Prop(world.nextId("p"), PropType.ROHR, 1, 3, "Leckendes Rohr", colorSlot = 7),
                Prop(world.nextId("p"), PropType.LAMPE, 3, 1, "Notlicht", colorSlot = 0, blocksTile = false),
            )
        )

        // Rückverweise der Gegenstände auf ihre Requisiten
        i1.holder = Item.holderProp(r1.props[0].id)
        i2.holder = Item.holderProp(r1.props[7].id)
        i3.holder = Item.holderProp(r2.props[0].id)

        world.addRoom(r1)
        world.addRoom(r2)
        world.addItem(i1)
        world.addItem(i2)
        world.addItem(i3)
        world.addFact("Spiel gestartet in ${r1.id}")

        return GameSave(
            name = name,
            world = world,
            roomId = r1.id,
            ptx = 2,
            pty = 4,
            playerLook = Look(BodyShape.TRAPEZ, colorA = 0, colorB = 3, headgear = Headgear.HUT),
        )
    }
}
