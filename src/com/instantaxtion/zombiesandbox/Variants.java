package com.instantaxtion.zombiesandbox;

import java.util.ArrayList;
import java.util.Random;

/**
 * The kinds of building a city can have beyond the basic house, shop, office, block and warehouse: cafés,
 * banks, factories, pubs, diners and the rest. Each belongs to some districts, and has its own roof, colours,
 * sign and inside.
 */
final class Variants {
    private Variants() {
    }

    // Roof styles (drawn by Roofs.drawVariant).
    static final int R_FLAT = 0, R_GABLE = 1, R_HIP = 2, R_PARAPET = 4, R_SAWTOOTH = 5, R_DOME = 6,
            R_GLASS = 7, R_GREEN = 8, R_POOL = 9, R_HELIPAD = 10, R_BARREL = 11, R_TILES = 13,
            R_ANTENNA = 14, R_STACKS = 15, R_TANKS = 16, R_SOLAR = 17, R_COURTYARD = 18, R_STRIPES = 19, R_CLOCK = 20,
            R_HOUSEPOOL = 21;

    // Insides (drawn by GameView).
    static final int I_HOME = 0, I_SHELVES = 1, I_CAFE = 2, I_RESTAURANT = 3, I_BAR = 4, I_OFFICE = 5, I_CLASSROOM = 6,
            I_WARD = 7, I_PEWS = 8, I_RACKS = 9, I_FACTORY = 10, I_HOTEL = 11, I_GYM = 12, I_LIBRARY = 13, I_BATH = 14,
            I_SEATS = 15, I_SALON = 16, I_LAUNDRY = 17, I_SHOWROOM = 18, I_ARCADE = 19, I_KITCHEN = 20, I_LAB = 21,
            I_DORM = 22, I_FIRE = 23;

    // Districts (bits by City.DT_ type).
    static final int DOWN = 1, MID = 2, OLD = 4, SUB = 8, IND = 16, UNI = 32, PARK = 64, TOWN = DOWN | MID | OLD | SUB | PARK,
            ANY = 127;

    static final class V {
        final String name, sign;
        final int base, districts, roof, roofCol, accent, inside, floorsMin, floorsMax, wall;

        V(String name, int base, int districts, int roof, int roofCol, int accent, String sign,
          int inside, int floorsMin, int floorsMax, int wall) {
            this.name = name;
            this.base = base;
            this.districts = districts;
            this.roof = roof;
            this.roofCol = roofCol;
            this.accent = accent;
            this.sign = sign;
            this.inside = inside;
            this.floorsMin = floorsMin;
            this.floorsMax = floorsMax;
            this.wall = wall;
        }
    }

    static final ArrayList<V> ALL_VARIANTS = new ArrayList<V>();
    /** How many variants there were before 10.1 (the ones pick() chooses from). */
    static int LEGACY;
    /** The random numbers for swap(), apart from the city's own (so adding variants never moves the streets). */
    static Random extra = new Random(1);


    private static void s(String name, int districts, int roof, int roofCol, int accent, String sign, int inside) {
        ALL_VARIANTS.add(new V(name, City.SHOP, districts, roof, roofCol, accent, sign, inside, 0, 0, 0));
    }

    private static void o(String name, int districts, int roof, int roofCol, int accent, String sign, int inside,
                          int fmin, int fmax) {
        ALL_VARIANTS.add(new V(name, City.OFFICE, districts, roof, roofCol, accent, sign, inside, fmin, fmax, 0));
    }

    private static void wh(String name, int districts, int roof, int roofCol, int accent, String sign, int inside) {
        ALL_VARIANTS.add(new V(name, City.WAREHOUSE, districts, roof, roofCol, accent, sign, inside, 0, 0, 0));
    }

    private static void hs(String name, int roof, int roofCol, int wall, int floors) {
        ALL_VARIANTS.add(new V(name, City.HOUSE, ANY, roof, roofCol, 0, null, I_HOME, floors, floors, wall));
    }

    private static void ap(String name, int districts, int roof, int roofCol, int wall, int inside, int fmin, int fmax) {
        ALL_VARIANTS.add(new V(name, City.APARTMENT, districts, roof, roofCol, 0, null, inside, fmin, fmax, wall));
    }

    static {
        // Shops found everywhere (or wherever the sign makes sense).
        s("Café", TOWN | PARK, R_STRIPES, 0xFF6E5A4A, 0xFF8A5A3A, "CAFÉ", I_CAFE);
        s("Bakery", TOWN, R_FLAT, 0xFF9A8A70, 0xFFC8963A, "BAKERY", I_KITCHEN);
        s("Butcher", TOWN, R_STRIPES, 0xFF8A6A60, 0xFFB03A2E, "BUTCHER", I_SHELVES);
        s("Florist", TOWN, R_GREEN, 0xFF6E7A5A, 0xFFE07AA0, "FLOWERS", I_SHELVES);
        s("Bookshop", MID | OLD | UNI, R_PARAPET, 0xFF5A5E66, 0xFF2E4F3A, "BOOKS", I_LIBRARY);
        s("Hardware store", TOWN | IND, R_FLAT, 0xFF7C848A, 0xFFE0702E, "HARDWARE", I_SHELVES);
        s("Hair salon", TOWN, R_STRIPES, 0xFF7A6A72, 0xFFE07AA0, "SALON", I_SALON);
        s("Laundromat", TOWN, R_FLAT, 0xFF8A9096, 0xFF3A8AE0, "LAUNDRY", I_LAUNDRY);
        s("Electronics store", DOWN | MID, R_PARAPET, 0xFF5A6670, 0xFF2E5FB0, "ELECTRONICS", I_SHELVES);
        s("Boutique", DOWN | MID | OLD, R_STRIPES, 0xFF6E5A7E, 0xFF2A2A2A, "BOUTIQUE", I_SHELVES);
        s("Shoe shop", DOWN | MID, R_STRIPES, 0xFF7E7A5C, 0xFF6E3A2A, "SHOES", I_SHELVES);
        s("Toy shop", MID | SUB, R_STRIPES, 0xFF8A6A3E, 0xFFE0C040, "TOYS", I_SHELVES);
        s("Pet shop", MID | SUB, R_FLAT, 0xFF7A8A6A, 0xFF3FA860, "PETS", I_SHELVES);
        s("Bike shop", MID | OLD | UNI, R_FLAT, 0xFF6A7078, 0xFF3FB8B0, "BIKES", I_SHOWROOM);
        s("Bar", DOWN | MID | OLD, R_FLAT, 0xFF3A3A40, 0xFF8A2A4A, "BAR", I_BAR);
        s("Restaurant", DOWN | MID | OLD, R_TILES, 0xFF8A4A32, 0xFF2A2A2A, "RESTAURANT", I_RESTAURANT);
        s("Pizzeria", TOWN, R_STRIPES, 0xFF8A5A3A, 0xFF2E8A3A, "PIZZA", I_RESTAURANT);
        s("Ice cream parlour", TOWN | PARK, R_STRIPES, 0xFFB8A8C8, 0xFFE07AA0, "GELATO", I_CAFE);
        s("Jeweller", DOWN | MID, R_PARAPET, 0xFF5A5E66, 0xFFD8B040, "JEWELLER", I_SHELVES);
        s("Optician", DOWN | MID, R_FLAT, 0xFF8A9096, 0xFF2E5FB0, "OPTICIAN", I_SALON);
        s("Dentist", MID | SUB, R_FLAT, 0xFFD8DCE0, 0xFF3FB8B0, "DENTIST", I_WARD);
        s("Gym", DOWN | MID | SUB, R_FLAT, 0xFF4A4E56, 0xFFE0702E, "GYM", I_GYM);
        s("Music shop", MID | OLD | UNI, R_STRIPES, 0xFF4A3A4A, 0xFFD8B040, "MUSIC", I_SHELVES);
        s("Convenience store", TOWN, R_FLAT, 0xFFB8BCC0, 0xFFE0402E, "24/7", I_SHELVES);
        s("Arcade", DOWN | MID, R_FLAT, 0xFF3A3A50, 0xFFE040C0, "ARCADE", I_ARCADE);

        // Public buildings and big offices.
        o("Post office", MID | OLD | SUB, R_PARAPET, 0xFF8A6A4A, 0xFFB03A2E, "POST", I_OFFICE, 1, 2);
        o("Bank", DOWN | MID | OLD, R_PARAPET, 0xFF8A8680, 0xFF2E4F3A, "BANK", I_OFFICE, 2, 4);
        o("Library", MID | OLD | UNI, R_DOME, 0xFF8A8A84, 0xFF6E5A3A, "LIBRARY", I_LIBRARY, 2, 3);
        o("Cinema", DOWN | MID, R_FLAT, 0xFF3A3A40, 0xFFE03A3A, "CINEMA", I_SEATS, 2, 3);
        o("Museum", DOWN | OLD | PARK, R_DOME, 0xFF9C9488, 0xFF6E5A3A, "MUSEUM", I_LIBRARY, 2, 3);
        o("Hotel", DOWN | MID | OLD, R_POOL, 0xFF8A8E96, 0xFFD8B040, "HOTEL", I_HOTEL, 5, 14);
        o("Clinic", MID | SUB, R_FLAT, 0xFFE4E8EC, 0xFF2FA84F, "CLINIC", I_WARD, 1, 3);
        o("Town hall", OLD | MID, R_CLOCK, 0xFF7A6A5A, 0xFFD8B040, "TOWN HALL", I_OFFICE, 2, 3);
        o("Courthouse", DOWN | OLD, R_DOME, 0xFF9C9488, 0xFF4A4E56, "COURT", I_SEATS, 2, 4);
        o("Theatre", DOWN | OLD, R_BARREL, 0xFF6A3A3A, 0xFFD8B040, "THEATRE", I_SEATS, 2, 3);
        o("Glass skyscraper", DOWN, R_GLASS, 0xFF5E7686, 0xFF9CC3D9, null, I_OFFICE, 18, 30);
        o("Office tower", DOWN | MID, R_HELIPAD, 0xFF6A6E74, 0xFFE8D24A, null, I_OFFICE, 12, 22);
        o("Bank headquarters", DOWN, R_ANTENNA, 0xFF4A4E56, 0xFF2E4F3A, "BANK", I_OFFICE, 16, 26);
        o("Hotel tower", DOWN, R_POOL, 0xFF7A7E86, 0xFFD8B040, "HOTEL", I_HOTEL, 14, 22);
        o("Department store", DOWN | MID, R_PARAPET, 0xFF8A8680, 0xFFB03A2E, "STORE", I_SHELVES, 4, 6);
        o("News tower", DOWN, R_ANTENNA, 0xFF5A5E66, 0xFFE03A3A, "NEWS", I_OFFICE, 12, 20);
        o("Convention centre", DOWN, R_BARREL, 0xFF9AA0A6, 0xFF2E5FB0, null, I_SEATS, 2, 3);
        o("Green-roof offices", DOWN | MID | UNI, R_GREEN, 0xFF6E7A5A, 0xFF3FA860, null, I_OFFICE, 4, 9);
        o("Solar offices", MID | SUB | IND, R_SOLAR, 0xFF8A8E94, 0xFF1F3A66, null, I_OFFICE, 3, 7);

        // Campus.
        o("Lecture hall", UNI, R_BARREL, 0xFF8A7A6A, 0xFF2E4F8A, "LECTURES", I_SEATS, 2, 3);
        o("University library", UNI, R_DOME, 0xFF8A8A84, 0xFF6E5A3A, "LIBRARY", I_LIBRARY, 3, 4);
        o("Science labs", UNI | IND, R_STACKS, 0xFF9AA0A6, 0xFF3FB8B0, "LABS", I_LAB, 3, 5);
        ap("Student halls", UNI, R_FLAT, 0xFF8A7A6A, 0xFFB86A48, I_DORM, 4, 7);
        o("Student union", UNI, R_TILES, 0xFF8A4A32, 0xFF2E4F8A, "UNION", I_CAFE, 2, 3);
        o("Sports hall", UNI | SUB, R_BARREL, 0xFF7C848A, 0xFFE0702E, "SPORTS", I_GYM, 2, 2);
        o("Observatory", UNI | PARK, R_DOME, 0xFFD8DCE0, 0xFF4A4E56, null, I_LAB, 2, 3);

        // Industry.
        wh("Factory", IND, R_SAWTOOTH, 0xFF8A9096, 0xFF9CC3D9, null, I_FACTORY);
        wh("Brewery", IND | OLD, R_TANKS, 0xFF8A6A4A, 0xFFD8B040, "BREWERY", I_FACTORY);
        wh("Cold storage", IND, R_FLAT, 0xFFE4E8EC, 0xFF3A8AE0, "COLD STORE", I_RACKS);
        wh("Recycling plant", IND, R_STACKS, 0xFF6F777D, 0xFF3FA860, "RECYCLING", I_FACTORY);
        wh("Steel works", IND, R_STACKS, 0xFF4A4E52, 0xFFE0702E, null, I_FACTORY);
        wh("Printing works", IND | MID, R_SAWTOOTH, 0xFF7C848A, 0xFF2A2A2A, "PRINT", I_FACTORY);
        wh("Bottling plant", IND, R_TANKS, 0xFF9AA0A4, 0xFF3A8AE0, null, I_FACTORY);
        wh("Data centre", IND | SUB, R_FLAT, 0xFF5A5E66, 0xFF3FB8B0, "DATA", I_RACKS);
        wh("Logistics hub", IND, R_FLAT, 0xFF8A9096, 0xFFE0C040, "LOGISTICS", I_RACKS);
        wh("Quonset store", IND | PARK, R_BARREL, 0xFF9AA0A4, 0xFF7C848A, null, I_RACKS);
        wh("Self storage", IND | SUB, R_PARAPET, 0xFFB8BCC0, 0xFFE0702E, "STORAGE", I_RACKS);
        wh("Car dealership", SUB | MID | IND, R_GLASS, 0xFF9CC3D9, 0xFFE03A3A, "CARS", I_SHOWROOM);
        wh("Big-box store", SUB | IND, R_PARAPET, 0xFFC8C4BC, 0xFF2E5FB0, "MEGA STORE", I_SHELVES);
        wh("Auto repair", IND | SUB | MID, R_SAWTOOTH, 0xFF7C7F82, 0xFFE0C040, "AUTO", I_SHOWROOM);
        wh("Bowling alley", SUB | MID, R_BARREL, 0xFF6A5A7A, 0xFFE03A3A, "BOWLING", I_GYM);

        // In the parks.
        o("Park café", PARK, R_HIP, 0xFF6E4A3A, 0xFF3FA860, "CAFÉ", I_CAFE, 1, 1);
        o("Glasshouse", PARK | UNI, R_GLASS, 0xFFA8D8B0, 0xFF3FA860, null, I_SHELVES, 1, 2);
        o("Gallery", PARK | OLD | MID, R_GLASS, 0xFFE8E8E8, 0xFF2A2A2A, "GALLERY", I_LIBRARY, 2, 3);

        // Homes everywhere.
        hs("Bungalow", R_HIP, 0xFF6E4A3A, 0xFFD8CBB0, 1);
        hs("Two-storey home", R_GABLE, 0xFF5A3D35, 0xFFE0D6C4, 2);
        hs("House with a pool", R_HOUSEPOOL, 0xFF8E3B2E, 0xFFE0D6C4, 1);
        hs("Solar house", R_SOLAR, 0xFF4E5560, 0xFFC9B79C, 2);
        hs("Duplex", R_GABLE, 0xFF6E4A3A, 0xFFB8A48A, 2);
        ap("Tower block", MID | SUB | DOWN, R_FLAT, 0xFF7C7F82, 0xFFB5A08A, I_HOME, 10, 16);
        ap("Low-rise flats", MID | SUB | OLD, R_PARAPET, 0xFF8A7A6A, 0xFFC9B8A0, I_HOME, 3, 4);
        ap("Courtyard block", MID | OLD | DOWN, R_COURTYARD, 0xFF7A6A5A, 0xFFD4C8B8, I_HOME, 4, 6);
        ap("Luxury flats", DOWN | MID, R_POOL, 0xFF6A6E74, 0xFFE2DCD0, I_HOME, 8, 14);

        s("Diner", TOWN | IND, R_STRIPES, 0xFFB8BCC0, 0xFFB83A30, "DINER", I_RESTAURANT);
        s("Burger drive-thru", SUB | MID | IND, R_STRIPES, 0xFFE0C040, 0xFFB83A30, "BURGERS", I_RESTAURANT);
        s("Donut shop", TOWN, R_STRIPES, 0xFFE07AA0, 0xFF8A5A3A, "DONUTS", I_CAFE);
        s("Pawn shop", MID | IND, R_FLAT, 0xFF6A6A60, 0xFFD8B040, "PAWN", I_SHELVES);
        s("Dollar store", SUB | MID, R_FLAT, 0xFF7A8A6A, 0xFF3FA860, "DOLLAR", I_SHELVES);
        s("Liquor store", MID | SUB, R_FLAT, 0xFF5A5E66, 0xFFE03A3A, "LIQUOR", I_SHELVES);
        o("Motel", SUB | IND | PARK, R_FLAT, 0xFF8A6A4A, 0xFFE03A3A, "MOTEL", I_HOTEL, 1, 2);
        hs("Ranch house", R_HIP, 0xFF7A5A48, 0xFFC9B79C, 1);
        hs("Colonial house", R_GABLE, 0xFF4E5560, 0xFFF2EEE4, 2);
        hs("Craftsman bungalow", R_GABLE, 0xFF5A3D35, 0xFF8A9A7A, 1);





        // (Everything above was in the game before 10.1; the city generator picks from it exactly as it
        // always did, so saved cities rebuild the same. What follows is swapped in afterwards, see swap().)
        LEGACY = ALL_VARIANTS.size();

        // More American kinds.
        s("Barber shop", TOWN, R_STRIPES, 0xFF8A3A3A, 0xFF2E5FB0, "BARBER", I_SALON);
        s("Tattoo parlor", MID | DOWN, R_FLAT, 0xFF2A2A30, 0xFFE03A3A, "TATTOO", I_SALON);
        s("Sports bar", DOWN | MID | SUB, R_FLAT, 0xFF3A3A40, 0xFF2E8A3A, "SPORTS BAR", I_BAR);
        s("Bail bonds", DOWN | MID, R_FLAT, 0xFF6A6A60, 0xFFF0C040, "BAIL BONDS", I_OFFICE);
        s("BBQ smokehouse", SUB | IND | MID, R_GABLE, 0xFF5A3A2A, 0xFFE0702E, "BBQ", I_RESTAURANT);
        s("Mattress store", SUB | MID, R_PARAPET, 0xFFC8C4BC, 0xFF2E5FB0, "MATTRESSES", I_SHOWROOM);
        o("Megachurch", SUB | MID, R_BARREL, 0xFFE4E0D8, 0xFF2E5FB0, "CHURCH", I_PEWS, 1, 2);
        wh("Auto parts store", IND | SUB, R_PARAPET, 0xFFB8BCC0, 0xFFE03A3A, "AUTO PARTS", I_SHELVES);
        wh("Grain elevator", PARK | IND, R_TANKS, 0xFFB8B4A8, 0xFF8A6A4A, null, I_RACKS);
        hs("Trailer home", R_FLAT, 0xFFD8DCE0, 0xFF9AA8B4, 1);
        ap("Brownstone", OLD | MID, R_PARAPET, 0xFF6A4A3A, 0xFF8A5A40, I_HOME, 4, 5);











        // And more for each kind of place: downtown, old town, parkland, industry, suburbs, campus.
        o("Stock exchange", DOWN, R_DOME, 0xFF9C9488, 0xFF2E4F3A, "EXCHANGE", I_OFFICE, 4, 6);
        o("Concert hall", DOWN | OLD, R_BARREL, 0xFF8A8680, 0xFFD8B040, "CONCERTS", I_SEATS, 3, 4);
        s("Antique shop", OLD, R_STRIPES, 0xFF6A5A4A, 0xFFD8B040, "ANTIQUES", I_SHELVES);
        o("Clock tower", OLD, R_CLOCK, 0xFF7A6A5A, 0xFFD8B040, null, I_OFFICE, 4, 5);
        o("Boathouse", PARK, R_GABLE, 0xFF5A4A3A, 0xFF2E6A8A, "BOATS", I_RACKS, 1, 1);
        wh("Garden centre", PARK | SUB, R_GLASS, 0xFFA8D8B0, 0xFF3FA860, "GARDEN", I_SHELVES);
        wh("Container depot", IND, R_STRIPES, 0xFF8A9096, 0xFFE0702E, "CONTAINERS", I_RACKS);
        wh("Lumber yard", IND | PARK, R_GABLE, 0xFF8A6A4A, 0xFFC8963A, "LUMBER", I_RACKS);
        wh("Cement works", IND, R_TANKS, 0xFFB8B4AC, 0xFF6F777D, null, I_FACTORY);
        o("Community centre", SUB | MID, R_FLAT, 0xFF8A8E94, 0xFF3FA860, "COMMUNITY", I_GYM, 1, 2);
        s("Vet clinic", SUB | MID, R_FLAT, 0xFFE4E8EC, 0xFF3FB8B0, "VET", I_WARD);
        o("Research institute", UNI | IND, R_SOLAR, 0xFF8A8E94, 0xFF3FB8B0, "INSTITUTE", I_LAB, 3, 6);
        s("Student bar", UNI, R_FLAT, 0xFF3A3A40, 0xFFE0C040, "BAR", I_BAR);

        // 10.3: more American kinds, and more for every kind of district.
        s("Sandwich deli", MID | DOWN, R_FLAT, 0xFF9AA0A6, 0xFF2E5FB0, "DELI", I_RESTAURANT);
        s("Bagel shop", TOWN, R_STRIPES, 0xFF3A4656, 0xFFE0C040, "BAGELS", I_CAFE);
        s("Thrift store", MID | SUB, R_FLAT, 0xFF7A6A5C, 0xFFE0702E, "THRIFT", I_SHELVES);
        s("Vape shop", MID | SUB, R_FLAT, 0xFF8A4A32, 0xFF2E5FB0, "VAPE", I_SHELVES);
        s("Nail salon", TOWN, R_FLAT, 0xFF5C6670, 0xFFE0C040, "NAILS", I_SALON);
        s("Check cashing", MID | IND, R_FLAT, 0xFF6A6E74, 0xFF7A5AC0, "CHECKS CASHED", I_OFFICE);
        s("Frozen yogurt", SUB | MID | PARK, R_STRIPES, 0xFF7A6A5C, 0xFFE0702E, "FROYO", I_CAFE);
        s("Steakhouse", DOWN | MID | SUB, R_GABLE, 0xFF866E5E, 0xFFB03A2E, "STEAKHOUSE", I_RESTAURANT);
        o("Elks lodge", SUB | MID, R_GABLE, 0xFF5C6670, 0xFF3FA860, "LODGE", I_BAR, 1, 2);
        o("Urgent care", SUB | MID, R_FLAT, 0xFF6A6E74, 0xFF2E4F3A, "URGENT CARE", I_WARD, 1, 1);
        wh("Feed store", PARK | IND, R_GABLE, 0xFF9AA0A6, 0xFF2E4F3A, "FEED & SEED", I_SHELVES);
        wh("Truck stop", IND | PARK, R_FLAT, 0xFF59646B, 0xFFE07AA0, "TRUCK STOP", I_RESTAURANT);
        hs("Split-level house", R_GABLE, 0xFF7C7C74, 0xFFD8CBB0, 2);
        hs("Cape Cod", R_GABLE, 0xFF3A4656, 0xFFD8CBB0, 2);
        ap("Garden apartments", SUB | MID, R_GABLE, 0xFF59646B, 0xFFB8A48A, I_HOME, 2, 3);
        // Every country: downtown.
        o("Law firm tower", DOWN, R_GLASS, 0xFF866E5E, 0xFF2E4F3A, null, I_OFFICE, 10, 18);
        o("Insurance building", DOWN, R_ANTENNA, 0xFF7C7C74, 0xFFD8B040, null, I_OFFICE, 8, 14);
        o("Media centre", DOWN, R_GLASS, 0xFFB0583A, 0xFF2E5FB0, "MEDIA", I_OFFICE, 6, 10);
        s("Luxury boutique", DOWN, R_PARAPET, 0xFF8A7F70, 0xFF3FA860, "LUXE", I_SHELVES);
        o("Rooftop-bar hotel", DOWN, R_POOL, 0xFF6A6E74, 0xFFB03A2E, "HOTEL", I_HOTEL, 10, 16);
        s("Wine bar", DOWN | OLD, R_FLAT, 0xFF8A4A32, 0xFFD8B040, "WINE", I_BAR);
        o("Co-working space", DOWN | MID, R_GREEN, 0xFF8A7F70, 0xFF7A5AC0, "COWORK", I_OFFICE, 4, 6);
        o("Embassy", DOWN, R_PARAPET, 0xFF6E5D5A, 0xFF2E5FB0, null, I_OFFICE, 3, 4);
        // Everyday streets.
        s("Tailor", MID | OLD, R_STRIPES, 0xFF866E5E, 0xFFD8B040, "TAILOR", I_SHELVES);
        s("Print shop", MID, R_FLAT, 0xFF6A6E74, 0xFFB03A2E, "PRINT", I_SHOWROOM);
        s("Locksmith", MID | SUB, R_FLAT, 0xFFB0583A, 0xFF3FB8B0, "KEYS", I_SHOWROOM);
        s("Phone repair", MID | DOWN, R_FLAT, 0xFF8A7F70, 0xFFE0702E, "REPAIR", I_SHOWROOM);
        s("Tea shop", MID | OLD, R_STRIPES, 0xFF8A4A32, 0xFF2E4F3A, "TEA", I_CAFE);
        s("Board game café", MID | UNI, R_FLAT, 0xFF9AA0A6, 0xFF3FB8B0, "GAMES", I_CAFE);
        o("Dance studio", MID, R_FLAT, 0xFF6A7468, 0xFF3FB8B0, "DANCE", I_GYM, 2, 2);
        o("Music school", MID | UNI, R_PARAPET, 0xFF9AA0A6, 0xFF2E4F3A, "MUSIC", I_CLASSROOM, 2, 3);
        s("Camera shop", MID | DOWN, R_FLAT, 0xFF866E5E, 0xFFB03A2E, "CAMERAS", I_SHELVES);
        // Old town.
        s("Watch repairer", OLD, R_STRIPES, 0xFF6E5D5A, 0xFF3FA860, "WATCHES", I_SHELVES);
        s("Tea room", OLD | PARK, R_STRIPES, 0xFF6A6E74, 0xFF2A2A2A, "TEA ROOM", I_CAFE);
        s("Apothecary", OLD, R_PARAPET, 0xFF8A7F70, 0xFFD8B040, "APOTHECARY", I_SHELVES);
        o("Guildhall", OLD, R_GABLE, 0xFF4F5A66, 0xFFE0702E, null, I_SEATS, 2, 3);
        o("Old customs house", OLD, R_PARAPET, 0xFF6A7468, 0xFFE0C040, null, I_OFFICE, 2, 3);
        s("Candle maker", OLD, R_TILES, 0xFF7C7C74, 0xFFE07AA0, "CANDLES", I_SHELVES);
        s("Map shop", OLD | UNI, R_STRIPES, 0xFF6E5D5A, 0xFF2E4F3A, "MAPS", I_LIBRARY);
        o("Puppet theatre", OLD, R_BARREL, 0xFF3A4656, 0xFF7A5AC0, "PUPPETS", I_SEATS, 1, 2);
        // Suburbs.
        s("Video rental", SUB, R_FLAT, 0xFF3A4656, 0xFF2A2A2A, "VIDEO", I_SHELVES);
        s("Takeaway pizza", SUB, R_STRIPES, 0xFF5C6670, 0xFF2E5FB0, "PIZZA", I_RESTAURANT);
        o("Kindergarten", SUB, R_FLAT, 0xFF4F5A66, 0xFFE0C040, "KINDERGARTEN", I_CLASSROOM, 1, 1);
        o("Retirement home", SUB | PARK, R_HIP, 0xFF8A4A32, 0xFF2A2A2A, null, I_WARD, 2, 3);
        o("Swimming pool", SUB | PARK, R_BARREL, 0xFF6A6E74, 0xFF2E4F3A, "POOL", I_BATH, 1, 1);
        s("Driving school", SUB | MID, R_FLAT, 0xFF7C7C74, 0xFFE0702E, "DRIVING SCHOOL", I_CLASSROOM);
        o("Scout hall", SUB, R_GABLE, 0xFF6E5D5A, 0xFF7A5AC0, null, I_GYM, 1, 1);
        // Industry.
        wh("Scrapyard", IND, R_FLAT, 0xFF8A7F70, 0xFF2A2A2A, "SCRAP", I_RACKS);
        wh("Paint factory", IND, R_STACKS, 0xFF8A4A32, 0xFFE03A3A, null, I_FACTORY);
        wh("Bakery plant", IND, R_FLAT, 0xFF4F5A66, 0xFF2E5FB0, "BAKERY", I_FACTORY);
        wh("Furniture factory", IND, R_SAWTOOTH, 0xFF9AA0A6, 0xFF2A2A2A, null, I_FACTORY);
        wh("Tyre depot", IND | SUB, R_FLAT, 0xFF4F5A66, 0xFFD8B040, "TYRES", I_RACKS);
        wh("Glassworks", IND, R_STACKS, 0xFF4F5A66, 0xFFE07AA0, null, I_FACTORY);
        wh("Truck wash", IND, R_FLAT, 0xFF5C6670, 0xFFE07AA0, "TRUCK WASH", I_SHOWROOM);
        wh("Bus depot", IND | MID, R_SAWTOOTH, 0xFF59646B, 0xFFE03A3A, "BUS DEPOT", I_SHOWROOM);
        wh("Ice factory", IND, R_FLAT, 0xFF6E5D5A, 0xFF2A2A2A, "ICE", I_RACKS);
        // Campus.
        o("Medical school", UNI, R_FLAT, 0xFF9AA0A6, 0xFFE07AA0, null, I_LAB, 4, 6);
        o("Engineering faculty", UNI | IND, R_SAWTOOTH, 0xFF7C7C74, 0xFFE03A3A, null, I_LAB, 3, 5);
        o("Law faculty", UNI, R_PARAPET, 0xFF4F5A66, 0xFFE03A3A, null, I_CLASSROOM, 3, 4);
        s("Campus bookstore", UNI, R_FLAT, 0xFF59646B, 0xFF2E5FB0, "BOOKSTORE", I_LIBRARY);
        o("Planetarium", UNI | PARK, R_DOME, 0xFF866E5E, 0xFF2A2A2A, null, I_SEATS, 1, 2);
        o("Innovation hub", UNI | DOWN, R_GLASS, 0xFF59646B, 0xFFE07AA0, null, I_OFFICE, 3, 6);
        // Parks.
        s("Kiosk café", PARK, R_STRIPES, 0xFF5C6670, 0xFF3FB8B0, "KIOSK", I_CAFE);
        o("Visitor centre", PARK, R_GREEN, 0xFF5C6670, 0xFFE03A3A, null, I_LIBRARY, 1, 1);
        o("Botanic glasshouse", PARK, R_GLASS, 0xFF3A4656, 0xFF7A5AC0, null, I_SHELVES, 1, 2);
        o("Riding stables", PARK, R_GABLE, 0xFF9AA0A6, 0xFFD8B040, null, I_RACKS, 1, 1);
        o("Park pavilion", PARK, R_HIP, 0xFF59646B, 0xFFE03A3A, null, I_CAFE, 1, 1);
        wh("Plant nursery", PARK | SUB, R_GLASS, 0xFF4F5A66, 0xFF3FB8B0, "NURSERY", I_SHELVES);
        o("Mini golf club", PARK, R_FLAT, 0xFF59646B, 0xFF2E4F3A, "MINI GOLF", I_GYM, 1, 1);
        government();
    }

    /** The government buildings (only ever chosen by {@link #gov}). */
    private static void government() {
        String[][] g = Country.gov();
        ALL_VARIANTS.add(new V("City hall", City.CITY_HALL, ANY, R_DOME, 0xFFD8D4C8, 0xFFD8B040, g[1][0], I_OFFICE, 3, 4, 0));
        ALL_VARIANTS.add(new V("Courthouse", City.COURTHOUSE, ANY, R_PARAPET, 0xFFC8C4B8, 0xFF2E4F3A, g[1][1], I_SEATS, 2, 3, 0));
        ALL_VARIANTS.add(new V("Jail", City.JAIL, ANY, R_FLAT, 0xFF7C7F82, 0xFF2A2A2A, g[1][2], I_DORM, 2, 2, 0));
        ALL_VARIANTS.add(new V("Emergency call centre", City.CALL_CENTRE, ANY, R_ANTENNA, 0xFF59646B, 0xFFE03A3A, g[1][3], I_OFFICE, 2, 3, 0));
        ALL_VARIANTS.add(new V("Public works depot", City.WORKS, ANY, R_SAWTOOTH, 0xFF8A7F70, 0xFFE0702E, g[1][4], I_SHOWROOM, 1, 1, 0));
        ALL_VARIANTS.add(new V("National Guard armory", City.ARMORY, ANY, R_BARREL, 0xFF5E6A48, 0xFF2E4F3A,
                Country.guard()[3], I_GYM, 2, 2, 0));
    }

    /** The look of a government building, or -1. */
    static int gov(int kind) {
        for (int i = LEGACY; i < ALL_VARIANTS.size(); i++) {
            V v = ALL_VARIANTS.get(i);
            if (v.base == kind) return i;
        }
        return -1;
    }

    /**
     * Picks a variant for a new lot, or -1 to keep the plain building. Big lots get the big buildings,
     * small lots the small ones.
     */
    static int pick(int kind, int district, int lw, int lh, Random rnd) {
        float chance;
        switch (kind) {
            case City.SHOP: chance = 0.85f; break;
            case City.OFFICE: chance = 0.5f; break;
            case City.WAREHOUSE: chance = 0.7f; break;
            case City.HOUSE: chance = 0.5f; break;
            case City.APARTMENT: chance = 0.5f; break;
            default: return -1;
        }
        if (rnd.nextFloat() > chance) return -1;
        int dbit = 1 << Math.max(0, Math.min(6, district));
        int area = lw * lh, n = 0, pickN = -1;
        for (int i = 0; i < LEGACY; i++) {
            V v = ALL_VARIANTS.get(i);
            if (v.base != kind || (v.districts & dbit) == 0) continue;
            // Skyscrapers and halls need a big lot; corner shops a small one.
            if (v.floorsMin >= 12 && area < 64) continue;
            if ((v.roof == R_BARREL || v.roof == R_COURTYARD || v.roof == R_DOME) && area < 36) continue;
            n++;
            if (rnd.nextInt(n) == 0) pickN = i;
        }
        return pickN;
    }

    /**
     * Now and then a lot gets one of the newer kinds of building instead (as often as there are newer kinds
     * to choose from, alongside the older ones). Returns the variant to use.
     */
    static int swap(int picked, int kind, int district, int lw, int lh) {
        if (picked < 0 && extra.nextFloat() > 0.5f) return picked;
        int dbit = 1 << Math.max(0, Math.min(6, district));
        int area = lw * lh, older = 0, newer = 0, pickN = -1;
        for (int i = 0; i < ALL_VARIANTS.size(); i++) {
            V v = ALL_VARIANTS.get(i);
            if (v.base != kind || (v.districts & dbit) == 0) continue;
            if (v.floorsMin >= 12 && area < 64) continue;
            if ((v.roof == R_BARREL || v.roof == R_COURTYARD || v.roof == R_DOME) && area < 36) continue;
            if (i < LEGACY) older++;
            else if (extra.nextInt(++newer) == 0) pickN = i;
        }
        if (pickN < 0 || extra.nextInt(older + newer) >= newer) return picked;
        return pickN;
    }

    static V get(int id) {
        return id >= 0 && id < ALL_VARIANTS.size() ? ALL_VARIANTS.get(id) : null;
    }
}
