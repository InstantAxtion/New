package com.instantaxtion.zombiesandbox;

/** The in-game patch notes log, newest version first. */
final class PatchNotes {
    static final String VERSION = "2.1";
    static final int VERSION_CODE = 9;

    /** Each entry: a heading, then one line per change. */
    static final String[][] NOTES = {
            {
                    "2.1  -  A living city",
                    "New buildings: rows of shops with striped awnings, gas stations, churches with steeples and churchyards, schools, fire stations with fire trucks, and supermarkets with big car parks. People can hide in the shops, churches, schools and supermarkets.",
                    "New parks: playgrounds, soccer fields, basketball and tennis courts, community gardens, skateparks and cemeteries.",
                    "New maps: Old Town (narrow streets, shops and churches), Small Town and Campus (schools and sports fields). Every map has its own mix of landmarks and parks.",
                    "Traffic: cars drive around town, brake for pedestrians, plough through zombies, and get abandoned when the driver is surrounded.",
                    "Pigeons in the parks and plazas scatter at gunfire, explosions or anyone walking up, and land somewhere else.",
                    "Fires: explosions set parked cars burning, sometimes blowing up a moment later. Gas pumps go up in a big secondary blast. Wrecks stay charred, and fire hurts anyone standing in it.",
            },
            {
                    "2.0  -  The city fights back",
                    "Police cruisers drive backup officers to incidents and army trucks carry reserve squads out of the base, running over zombies on the way.",
                    "Air support: a helicopter flies from the base helipad and circles the fight with a door gunner. Sorties are limited.",
                    "Civilians barricade themselves in homes, offices and warehouses. Zombies batter the doors down, and people come out once the street is quiet. If someone infected turns inside, the building is lost.",
                    "Guards stand at the police station doors and the base gates.",
                    "Some civilians own a gun, and anyone can pick up a weapon dropped by a fallen cop or soldier.",
                    "Ammo is limited. Units out of ammo radio in and head back to the station or base to resupply.",
                    "Hand-to-hand: anyone can shove a zombie away and stun it, and cops and soldiers hit back with their weapon.",
                    "Medics and a hospital: medics heal the hurt and can cure fresh bites. Badly hurt people walk to the hospital.",
                    "Zombies are drawn to gunfire and explosions. New types: crawlers (hard to hit, and what's left after a blast) and screamers (their shriek calls every zombie nearby).",
                    "Orders tool: tap a cop or soldier (soldiers bring their whole squad), then tap where to send them. Tap them again to release them.",
                    "Save and load: save from the pause menu. The game also saves itself when you leave, and Continue picks it up.",
                    "Stats screen: people vs zombies over time, plus totals.",
                    "How to Play: a short tutorial on the first game, and in the main menu any time.",
                    "The stats panel collapses with a tap, the radio feed is shorter on phones, and people are easier to tell apart when zoomed out.",
                    "The Zombie button covers all zombie types: tap it again to switch.",
            },
            {
                    "1.6  -  Simpler cities",
                    "Water is gone: no more rivers, islands, ponds or fountains. Plazas have statues instead.",
                    "It's always daytime. The sunset and night modes (and the orange haze) are removed.",
                    "The New Game screen is down to 7 options: map, map size, civilians, cops, military, zombies and reinforcements.",
                    "Maps: Classic, Downtown, Suburbs, Industrial and Parkland. Each one decides its own buildings, street layout, parks, police stations and military base.",
            },
            {
                    "1.5  -  Limits",
                    "Safe zones have a capacity: 25 in a park zone, 35 at a police station, 40 for a military zone and 60 at a base. Labels show how full each one is.",
                    "Full zones radio that they're turning people away, and civilians head for a zone with room or keep running.",
                    "Reinforcements are now a small, fixed reserve that never refills. Choose Off, Low, Medium or High on the New Game screen (Medium: 2 backup waves of officers and 2 army squads of 4).",
                    "The stats panel shows the reserves left, and the radio says when the last one is sent.",
                    "Ponds no longer cut through park footpaths.",
                    "Rivers keep a grassy bank: no building stands on or right next to the water.",
            },
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
