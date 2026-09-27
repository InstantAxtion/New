package com.instantaxtion.zombiesandbox;

/** The in-game patch notes log, newest version first. */
final class PatchNotes {
    static final String VERSION = "1.2";
    static final int VERSION_CODE = 3;

    /** Each entry: a heading, then one line per change. */
    static final String[][] NOTES = {
            {
                    "1.2  -  Menus, sound and new maps",
                    "Main menu with a live zombie city running in the background.",
                    "Pause menu: resume, start a new game, open settings or go back to the main menu.",
                    "Settings: music and sound volume, 3D buildings, blood, health bars, screen shake, FPS counter and max population. Saved between sessions.",
                    "Music: a dark ambient theme for the menus and a tense track while you play.",
                    "Sound effects: pistols, rifles, explosions, bites, zombie groans and screams, panned by where they happen on screen.",
                    "New Game screen with 8 map presets: Classic, Downtown, Suburbs, Industrial, Riverside, Island, Parkland and Night City.",
                    "Custom cities: map size, building style, density, building height, parks, water, traffic, time of day and the starting number of civilians, cops, military and zombies.",
                    "New building styles: suburban houses with pitched roofs and yards, and warehouses with loading yards and roll-up doors.",
                    "Rivers with bridges, and island maps surrounded by beaches and ocean. Zombies can't swim.",
                    "Sunset and night modes. At night, street lamps glow, windows light up and muzzle flashes light the streets.",
                    "Skyscrapers up to 22 floors in the Downtown preset.",
                    "This patch notes log.",
            },
            {
                    "1.1  -  GTA 2 style buildings",
                    "Buildings have real height: walls lean away from the middle of the screen as you move around.",
                    "Windows on every floor, some lit, plus glass shopfronts at street level.",
                    "Downtown towers up to 13 floors; taller buildings cast longer shadows.",
                    "Tree canopies stand above the ground and can hide people underneath.",
                    "Portrait mode: the game now follows your phone's rotation.",
            },
            {
                    "1.0  -  First release",
                    "Procedurally generated city with roads, parks, plazas and parking lots.",
                    "Spawn civilians, cops, military, zombies, runners and brutes.",
                    "Zombie bites infect; anyone killed by a zombie rises again.",
                    "Cops and soldiers hunt zombies; soldiers throw grenades at crowds.",
                    "Bomb and erase tools, follow cam, pause, speed and brush size.",
            },
    };

    private PatchNotes() {
    }
}
