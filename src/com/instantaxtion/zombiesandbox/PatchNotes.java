package com.instantaxtion.zombiesandbox;

/** The in-game patch notes log, newest version first. */
final class PatchNotes {
    static final String VERSION = "1.4";
    static final int VERSION_CODE = 5;

    /** Each entry: a heading, then one line per change. */
    static final String[][] NOTES = {
            {
                    "1.4  -  Less grid, more city",
                    "New Street layout option: Grid, Varied or Organic.",
                    "Varied and Organic cities merge some blocks into superblocks, so streets have T-junctions and blocks come in different sizes.",
                    "Boulevards: wide avenues with a tree-lined median.",
                    "Rivers now wind across the map instead of running straight. Roads cross on bridges, and riverside buildings make way for parks.",
                    "Islands have a rounded, irregular coastline with beaches. Roads end at the sand.",
                    "Parks can have ponds and curved footpaths.",
                    "Rounded curbs at block corners.",
                    "Police stations: a precinct with parked cruisers. Some cops start there, backup officers leave from there, and police set up their safe zone there first.",
                    "Military bases: a fenced compound with gates, barracks, a helipad, tents, a watchtower and army trucks. Soldiers start on base, squads deploy from its gate, and the base becomes the military safe zone.",
                    "Idle soldiers return to base when there's nothing to fight.",
                    "New Game options for the number of police stations (0-3) and a military base.",
            },
            {
                    "1.3  -  911, radio and safe zones",
                    "911 calls: civilians who spot zombies call it in. Dispatch opens an incident at the nearest street corner and sends the closest free cops.",
                    "Cops report when they're en route, on scene and when the area is clear. Dispatch calls it in when an officer goes down.",
                    "Police and military talk on the radio: big incidents, downed officers or a safe zone under attack make police request military support, and the military sends a squad.",
                    "Reinforcements: if nobody is free, backup officers or a military squad arrive from the edge of the map. Can be turned off on the New Game screen.",
                    "Safe zones: police and military set up guarded zones with sandbags in parks, plazas and parking lots. Guards take posts around the edge and face outward.",
                    "Civilians run to safe zones when they're scared or hear about one on the news, and shelter inside.",
                    "Safe zones can be overrun. When a police zone falls, officers fall back to a military zone.",
                    "New Safe Zone tool: tap anywhere to order one. The Erase tool on a zone's centre closes it.",
                    "Radio feed on screen. Tap a message to jump the camera there. Can be turned off in Settings.",
                    "Street names, callsigns (Unit 14, Bravo-2), phone and radio icons above people, and 911 markers on the map.",
                    "New sounds: phones ringing and radio chatter.",
                    "Games now start with the Move tool selected.",
            },
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
