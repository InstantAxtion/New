package com.instantaxtion.zombiesandbox;

import java.util.Random;

/**
 * Where the city is: the look of its buildings, its street, town and people's names, the road markings and the
 * police cars. (Since 11.3 the game is set in the USA only: the other countries and their code are gone.)
 */
final class Country {
    static final int USA = 0;
    static final String[] NAMES = {"USA"};
    static final String[] INFO = {"Wide avenues with yellow centre lines, clapboard houses and black-and-white cruisers."};

    /** The country of the game being played (for people's names). */
    static volatile Country current;

    final int id = USA;
    /** The highway route shield's colour. */
    final int shieldColor = 0xFF1E3A8A;
    /** Colour of the centre line on ordinary streets. */
    final int centreLine;
    final int[] houseRoofs, houseWalls, shopRoofs, shopWalls, apartmentWalls;
    /** Police cars: body colour, door panels, a contrasting roof (0 for none), and a chequered band down the sides. */
    int cruiserBody, cruiserDoor, cruiserRoof;
    boolean cruiserChecks;
    /** The two colours of the chequered band. */
    int checkA, checkB;
    final String[] streets, mainWords, localWords;
    final String ringRoad;
    final String[] townStart, townEnd, districtWords, hamletEnds;
    final String[] churches, schools, markets, pharmacies, gunStores, bases;
    final String hospital, fireStation;
    String policeStation;
    final String[] first, last, dogs;
    /** Pavement, grass and plaza colours (0 keeps the usual), and roofs/walls for offices and old buildings (null keeps the usual). */
    int pavement, grass, plaza;
    /** The everyday traffic: a weighted list of Fleet car models, their paint, and the local taxis and buses. */
    int[] carModels, carColors;
    int taxiColor, busColor;
    int[] officeRoofs, officeWalls, oldWalls, oldRoofs;

    private static Country usa;

    /** The country (whatever the id: there is only the one now). */
    static Country get(int id) {
        synchronized (NAMES) {
            if (usa == null) usa = new Country();
            return usa;
        }
    }

    /** A street name: "Oak St". */
    String streetName(String base, boolean main, Random rnd) {
        String[] types = main ? mainWords : localWords;
        String type = types[rnd.nextInt(types.length)];
        if (type.isEmpty()) return base;
        return base + " " + type;
    }

    /** The sign over a gun store's door. */
    String gunSign() {
        return "GUNS";
    }

    // ------------------------------------------------------------------ out in the country

    /** Names for the country's wild places: its mountains, lakes, falls and parks. */
    String[] natureWords() {
        return new String[]{"Bear", "Eagle", "Pine", "Cedar", "Willow", "Elk", "Hawk", "Silver", "Crystal", "Thunder", "Wolf", "Aspen", "Granite", "Misty", "Echo", "Raven"};
    }

    /** The nature park ("Yellow Pine State Park"), or with no word, what its visitor centre is called. */
    String parkName(String word) {
        return word == null ? "Visitor Center" : word + " State Park";
    }

    String mountainName(String word) {
        return "Mount " + word;
    }

    String lakeName(String word) {
        return "Lake " + word;
    }

    String fallsName(String word) {
        return word + " Falls";
    }

    /** The park rangers' station. */
    String rangerStationName(String park) {
        return "Ranger Station" + (park != null ? ", " + park : "");
    }

    /** A fire lookout tower. */
    String lookoutName(String word) {
        return word + " Fire Lookout";
    }

    String campName(String word) {
        return word + " Campground";
    }

    /** The SWAT team: {its name, what its officers are called}. */
    static String[] swat() {
        return new String[]{"SWAT", "SWAT officer"};
    }

    /** The National Guard: {its armory, the force, who calls it out, the armory's roof sign}. */
    static String[] guard() {
        return new String[]{"National Guard Armory", "National Guard", "Governor", "NATIONAL GUARD"};
    }

    /** The government buildings: {names}, then {roof signs}. */
    static String[][] gov() {
        return new String[][]{{"City Hall", "County Courthouse", "County Jail", "911 Dispatch Center", "Public Works Yard"},
                {"CITY HALL", "COURTHOUSE", "COUNTY JAIL", "911", "PUBLIC WORKS"}};
    }

    /** A sign on a roof. */
    String sign(String english) {
        return english;
    }

    String townName(Random r) {
        String a = townStart[r.nextInt(townStart.length)], b = townEnd[r.nextInt(townEnd.length)];
        // No "Champchamp".
        if (a.toLowerCase().contains(b.trim().toLowerCase()) || b.toLowerCase().contains(a.trim().toLowerCase()))
            b = townEnd[(java.util.Arrays.asList(townEnd).indexOf(b) + 1) % townEnd.length];
        return a + b;
    }

    /** A neighbourhood's name. The type is a City district type, -1 for a plain one or -2 for the countryside. */
    String districtName(String english, String word, int type) {
        return english;
    }

    private Country() {
        centreLine = 0xFFD9B43A;
        houseRoofs = new int[]{0xFF8E3B2E, 0xFF6E4A3A, 0xFF4E5560, 0xFF7A5A48, 0xFF9A5B3C, 0xFF5A3D35};
        houseWalls = new int[]{0xFFD8CBB0, 0xFFC9B79C, 0xFFE0D6C4, 0xFFB8A48A, 0xFFA7B4BE, 0xFFC7A9A0};
        shopRoofs = new int[]{0xFF8C5A4A, 0xFF5A6E8C, 0xFF7E7A5C, 0xFF6E5A7E, 0xFF8A6A3E, 0xFF4F6F66};
        shopWalls = new int[]{0xFFE0C9A6, 0xFFB9C6D2, 0xFFD8B8A8, 0xFFC9D6B8, 0xFFE8DCC8, 0xFFB8A8C8};
        apartmentWalls = new int[]{0xFFC9B8A0, 0xFFB5A08A, 0xFFD4C8B8, 0xFFA89484, 0xFFBFB0C0};
        cruiserBody = 0xFF1C1D22;
        cruiserDoor = 0xFFEDEDED;
        cruiserRoof = 0;
        cruiserChecks = false;
        streets = new String[]{"Main", "Oak", "Pine", "Elm", "Maple", "Cedar", "Lake", "Hill", "Park",
                "Church", "Market", "Mill", "King", "Queen", "Walnut", "Spruce", "Birch", "Chestnut", "Harbor", "Union",
                "Rose", "Ash", "Willow", "Station", "River", "Bridge", "High", "Garden", "Orchard", "Victoria", "Albert",
                "Greene", "Water", "Grove", "Meadow", "Forest", "Bay", "Summit", "Liberty", "Franklin", "Madison",
                "Jackson", "Lincoln", "Hawthorn", "Laurel", "Poplar", "Sycamore", "Holly", "Juniper", "Linden"};
        mainWords = new String[]{"Ave", "Blvd", "Rd", "Pkwy"};
        localWords = new String[]{"St", "St", "St", "Ln", "Way", "Pl", "Ct", "Dr"};
        ringRoad = "Ring Rd";
        townStart = new String[]{"Ash", "Maple", "Oak", "Stone", "Clay", "Wolf", "Fair", "Green", "North",
                "Silver", "Iron", "Elder", "Hollow", "Red", "Kings", "Bright", "Glen", "Hazel", "Thorn", "Mill"};
        townEnd = new String[]{"ford", "field", "ville", "wood", "ton", "bury", "ridge", "haven", "dale",
                "stead", "wick", "gate", "hurst", "moor"};
        districtWords = new String[]{"Oak", "Cedar", "Mill", "North", "South", "East", "West", "King's",
                "Linden", "Ash", "Fox", "Hazel", "Stone", "Maple", "Willow", "Brook", "Elm", "High", "Rose", "Birch"};
        hamletEnds = new String[]{" Green", " End", " Cross", " Hamlet", " Farm", " Corner", " Bridge", " Hollow"};
        churches = new String[]{"St. Mary's Church", "St. Luke's Church", "St. Peter's Church", "Grace Church",
                "St. Anne's Church", "Trinity Church", "St. Mark's Church", "Holy Cross Church"};
        schools = new String[]{"Lincoln High", "Westside Elementary", "Roosevelt Middle School",
                "Jefferson High", "Oakwood Academy", "Hamilton Elementary"};
        markets = new String[]{"FreshMart", "ValueFoods", "Corner Grocer", "SuperSaver", "GreenBasket", "MegaMart"};
        pharmacies = new String[]{"CityCare Pharmacy", "Main Street Drugs", "HealthPlus", "Corner Pharmacy"};
        gunStores = new String[]{"Liberty Guns", "Ace Firearms", "Frontier Outfitters", "Hunter's Supply", "Patriot Arms"};
        bases = new String[]{"Fort Mercer", "Camp Redstone", "Fort Kessler", "Camp Hollow", "Fort Whitmore",
                "Camp Ironwood"};
        hospital = "City Hospital";
        carModels = new int[]{0, 0, 0, 1, 1, 1, 2, 2, 2, 5, 6, 7};
        carColors = new int[]{0xFFB03A2E, 0xFF2E5FB0, 0xFFE0E0E0, 0xFF222428, 0xFFD4A21C, 0xFF3C8A4E,
                0xFF8A8F96, 0xFF6B2E8A, 0xFFE07A2E};
        taxiColor = 0xFFE8B81A;
        busColor = 0xFFF2B21A;
        policeStation = "Precinct ";
        fireStation = "Fire Station ";
        first = new String[]{"Maria", "James", "Aisha", "Tom", "Sofia", "Daniel", "Mei", "Carlos",
                "Emma", "Omar", "Grace", "Liam", "Priya", "Noah", "Zoe", "Ivan", "Hana", "Lucas", "Nora", "Sam", "Ruth",
                "Kwame", "Elena", "Ben", "Yuki", "Diego", "Chloe", "Marcus", "Leila", "Owen", "Anna", "Jamal", "Rosa",
                "Felix", "Ines", "Hugo", "Tara", "Victor", "Lena", "Eli"};
        last = new String[]{"Lopez", "Smith", "Khan", "Nguyen", "Brown", "Garcia", "Kim", "Okafor",
                "Miller", "Rossi", "Novak", "Silva", "Cohen", "Walker", "Tanaka", "Murphy", "Patel", "Jensen", "Moreau",
                "Haddad", "Clarke", "Santos", "Weber", "Reyes", "Hughes", "Ali", "Kowalski", "Park", "Diaz", "Stone"};
        dogs = new String[]{"Rex", "Bella", "Max", "Luna", "Buddy", "Daisy", "Rocky", "Molly",
                "Scout", "Pepper", "Bear", "Rosie", "Duke", "Nala", "Biscuit", "Ziggy"};
        agencies();
    }

    /** Kinds of tree (City.treeKind): broadleaf and pine. */
    static final byte TK_BROAD = 0, TK_PINE = 1;
    /**
     * The land (cities built since 10.10): how hilly it is, how big its mountains are, how wooded, what share of
     * the woods are pines, how many lakes, how far up the mountains the snow starts (as a share of the highest
     * peak), and the trees of the lowlands.
     */
    final float hills = 1f, mountains = 1f, forest = 1f, pineShare = 0.5f, lakes = 1f, snowLine = 0.86f;
    final byte lowTree = TK_BROAD;

    /** Highways, as {name, what's on the signs}. */
    String[][] highways;
    /**
     * Who polices the highway and the countryside: the force's name, what an officer is called on the radio,
     * and their car's body and door colours.
     */
    String hpName, hpShort, ruralName, ruralShort;
    int hpBody, hpDoor, hpShirt, ruralBody, ruralDoor, ruralShirt;

    private void agencies() {
        highways = new String[][]{{"Interstate 80", "I-80"}, {"Interstate 40", "I-40"}, {"Interstate 95", "I-95"}, {"Interstate 70", "I-70"}};
        hpName = "Highway Patrol";
        hpShort = "Trooper";
        hpBody = 0xFFC8B48A;
        hpDoor = 0xFF3A2E22;
        hpShirt = 0xFFC8B48A;
        ruralName = "Sheriff";
        ruralShort = "Deputy";
        ruralBody = 0xFFF2F2F2;
        ruralDoor = 0xFF2E5A3A;
        ruralShirt = 0xFF6A5A3A;
    }
}
