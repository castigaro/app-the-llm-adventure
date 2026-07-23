package com.appsonar.thellmadventure.world

import com.appsonar.thellmadventure.data.GameSave

/**
 * Hartkodierte Demo-Außenwelt: zwei Parzellen (Naht mit Weg), ein Gebäude
 * mit fertigem Innenraum und ein zweites mit unentdecktem Inneren. Dient als
 * Offline-Spielwiese für Renderer, Nähte, Nebelränder, Gebäude-Ein-/Austritt
 * und Persistenz — die echten Welten erschafft die KI.
 */
object DemoWorld {

    fun newSave(name: String): GameSave {
        val world = WorldState()
        val outdoor = OutdoorMap()
        world.outdoor = outdoor
        val g = Limits.PARZELLE

        // Parzelle g1 (0,0): Wiese mit Weg nach Osten und kleinem Teich.
        val g1 = Parcel(
            id = world.nextId("g"),
            gx = 0,
            gy = 0,
            name = "Sturmwiese",
            palette = Palette.MOOS,
        )
        val zeilenG1 = listOf(
            "gggggggggggg",
            "ggggggbbgggg",
            "ggg~~ggggggg",
            "gg~~~ggggggg",
            "ggg~~ggggegg",
            "ggggggwwwwww",
            "gggwwwwggggg",
            "gggggggggggg",
            "ggbbggggggse",
            "gggggggggsse",
            "gggggggggggg",
            "gggggggggggg",
        )
        zeilenG1.forEachIndexed { ly, zeile ->
            zeile.forEachIndexed { lx, zeichen ->
                g1.setTerrainLocal(lx, ly, Terrain.fromZeichen(zeichen) ?: Terrain.GRAS)
            }
        }
        outdoor.addParcel(g1)

        // Parzelle g2 (1,0): Ufergebiet — der Weg läuft an der Naht weiter.
        val g2 = Parcel(
            id = world.nextId("g"),
            gx = 1,
            gy = 0,
            name = "Uferpfad",
            palette = Palette.SAND,
        )
        val zeilenG2 = listOf(
            "gggggss~~~~~",
            "ggggsss~~~~~",
            "gggggss~~~~~",
            "ggggggss~~~~",
            "ggggggss~~~~",
            "wwwggggss~~~",
            "gggwwwggss~~",
            "gggggwwggs~~",
            "ggggggggss~~",
            "gggggggss~~~",
            "ggggggss~~~~",
            "gggggss~~~~~",
        )
        zeilenG2.forEachIndexed { ly, zeile ->
            zeile.forEachIndexed { lx, zeichen ->
                g2.setTerrainLocal(lx, ly, Terrain.fromZeichen(zeichen) ?: Terrain.GRAS)
            }
        }
        outdoor.addParcel(g2)

        // Innenraum der Kate (fertig generiert — Rücktür zeigt nach draußen).
        val r1 = Room(
            id = world.nextId("r"),
            name = "Stube der Kate",
            w = 8,
            h = 6,
            palette = Palette.ROST,
        )

        // Gebäude: bewohnte Kate (Inneres fertig) + verfallener Turm ("?").
        val kate = Building(
            id = world.nextId("b"),
            name = "Alte Kate",
            tx = 2,
            ty = 7,
            w = 2,
            h = 2,
            stil = BuildingStyle.HUETTE,
            colorSlot = 6,
            targetRoomId = r1.id,
        )
        outdoor.buildings[kate.id] = kate
        g1.buildingIds.add(kate.id)

        val turm = Building(
            id = world.nextId("b"),
            name = "Verfallener Turm",
            tx = g + 2,
            ty = 2,
            w = 2,
            h = 2,
            stil = BuildingStyle.TURM,
            colorSlot = 7,
            targetRoomId = null,
        )
        outdoor.buildings[turm.id] = turm
        g2.buildingIds.add(turm.id)

        r1.doors.add(Door(Wall.S, 4, targetRoomId = "welt:${kate.id}", label = "Nach draußen"))
        r1.doors.add(Door(Wall.O, 2, targetRoomId = null, label = "Kellerluke"))

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

        // Requisiten draußen (globale Kacheln!)
        val truhe = Prop(world.nextId("p"), PropType.TRUHE, 6, 2, "Vergrabene Truhe", colorSlot = 6, states = mutableMapOf("verschlossen" to true), itemId = i1.id)
        g1.props.addAll(
            listOf(
                truhe,
                Prop(world.nextId("p"), PropType.STEIN, 9, 3, "Bemooster Findling", colorSlot = 7),
                Prop(world.nextId("p"), PropType.PFLANZE, 1, 4, "Windzerzauster Busch", colorSlot = 2),
                Prop(world.nextId("p"), PropType.BRUNNEN, 8, 8, "Alter Ziehbrunnen", colorSlot = 5),
            )
        )
        val fass = Prop(world.nextId("p"), PropType.FASS, g + 1, 8, "Angespültes Fass", colorSlot = 6, itemId = i3.id)
        g2.props.addAll(
            listOf(
                fass,
                Prop(world.nextId("p"), PropType.ANKER, g + 6, 9, "Rostiger Anker", colorSlot = 7),
            )
        )

        // Requisiten in der Stube (lokale Raumkacheln)
        val kiste = Prop(world.nextId("p"), PropType.KISTE, 2, 2, "Sperrholzkiste", colorSlot = 6, itemId = i2.id)
        r1.props.addAll(
            listOf(
                kiste,
                Prop(world.nextId("p"), PropType.BETT, 6, 1, "Durchgelegenes Bett", colorSlot = 4),
                Prop(world.nextId("p"), PropType.OFEN, 1, 1, "Kalter Ofen", colorSlot = 7),
                Prop(world.nextId("p"), PropType.TEPPICH, 4, 3, "Abgetretener Läufer", colorSlot = 4, blocksTile = false),
            )
        )

        // Eine Figur draußen an der Naht — Dialog-Spielwiese.
        val npc = Npc(
            id = world.nextId("n"),
            name = "Fährfrau Ottil",
            roomId = g2.id,
            tx = g + 4,
            ty = 7,
            look = Look(BodyShape.ROBE, colorA = 5, colorB = 3, headgear = Headgear.KAPUZE),
            charakterkarte = "Wortkarg, kennt jede Sandbank. Wartet seit Jahren auf ein bestimmtes Boot.",
            rolle = "Fährfrau",
        )
        world.addNpc(npc)
        g2.npcIds.add(npc.id)

        // Rückverweise der Gegenstände auf ihre Requisiten
        i1.holder = Item.holderProp(truhe.id)
        i2.holder = Item.holderProp(kiste.id)
        i3.holder = Item.holderProp(fass.id)

        world.addRoom(r1)
        world.addItem(i1)
        world.addItem(i2)
        world.addItem(i3)
        world.addFact("Spiel gestartet auf ${g1.id} ${g1.name}")

        return GameSave(
            name = name,
            world = world,
            roomId = GameSave.ORT_DRAUSSEN,
            ptx = 5,
            pty = 6,
            playerLook = Look(BodyShape.TRAPEZ, colorA = 0, colorB = 3, headgear = Headgear.HUT),
        )
    }
}
