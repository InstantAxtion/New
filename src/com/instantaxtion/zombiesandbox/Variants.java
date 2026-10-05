package com.instantaxtion.zombiesandbox;

import java.util.ArrayList;
import java.util.Random;

/**
 * The kinds of building a city can have beyond the basic house, shop, office, block and warehouse: cafés,
 * banks, factories, pubs, konbini, boulangeries, taquerías and the rest. Each belongs to some countries and
 * some districts, and has its own roof, colours, sign and inside.
 */
final class Variants {
    private Variants() {
    }

    // Roof styles (drawn by Roofs.drawVariant).
    static final int R_FLAT = 0, R_GABLE = 1, R_HIP = 2, R_MANSARD = 3, R_PARAPET = 4, R_SAWTOOTH = 5, R_DOME = 6,
            R_GLASS = 7, R_GREEN = 8, R_POOL = 9, R_HELIPAD = 10, R_BARREL = 11, R_PAGODA = 12, R_TILES = 13,
            R_ANTENNA = 14, R_STACKS = 15, R_TANKS = 16, R_SOLAR = 17, R_COURTYARD = 18, R_STRIPES = 19, R_CLOCK = 20,
            R_HOUSEPOOL = 21;

    // Insides (drawn by GameView).
    static final int I_HOME = 0, I_SHELVES = 1, I_CAFE = 2, I_RESTAURANT = 3, I_BAR = 4, I_OFFICE = 5, I_CLASSROOM = 6,
            I_WARD = 7, I_PEWS = 8, I_RACKS = 9, I_FACTORY = 10, I_HOTEL = 11, I_GYM = 12, I_LIBRARY = 13, I_BATH = 14,
            I_SEATS = 15, I_SALON = 16, I_LAUNDRY = 17, I_SHOWROOM = 18, I_ARCADE = 19, I_KITCHEN = 20, I_LAB = 21,
            I_DORM = 22, I_FIRE = 23;

    // Countries (bits) and districts (bits by City.DT_ type).
    static final int US = 1, AU = 2, JP = 4, FR = 8, MX = 16, ALL = 31, EN = US | AU | JP;
    static final int DOWN = 1, MID = 2, OLD = 4, SUB = 8, IND = 16, UNI = 32, PARK = 64, TOWN = DOWN | MID | OLD | SUB | PARK,
            ANY = 127;

    static final class V {
        final String name, sign;
        final int base, countries, districts, roof, roofCol, accent, inside, floorsMin, floorsMax, wall;

        V(String name, int base, int countries, int districts, int roof, int roofCol, int accent, String sign,
          int inside, int floorsMin, int floorsMax, int wall) {
            this.name = name;
            this.base = base;
            this.countries = countries;
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

    private static void s(String name, int countries, int districts, int roof, int roofCol, int accent, String sign, int inside) {
        ALL_VARIANTS.add(new V(name, City.SHOP, countries, districts, roof, roofCol, accent, sign, inside, 0, 0, 0));
    }

    private static void o(String name, int countries, int districts, int roof, int roofCol, int accent, String sign, int inside,
                          int fmin, int fmax) {
        ALL_VARIANTS.add(new V(name, City.OFFICE, countries, districts, roof, roofCol, accent, sign, inside, fmin, fmax, 0));
    }

    private static void wh(String name, int countries, int districts, int roof, int roofCol, int accent, String sign, int inside) {
        ALL_VARIANTS.add(new V(name, City.WAREHOUSE, countries, districts, roof, roofCol, accent, sign, inside, 0, 0, 0));
    }

    private static void hs(String name, int countries, int roof, int roofCol, int wall, int floors) {
        ALL_VARIANTS.add(new V(name, City.HOUSE, countries, ANY, roof, roofCol, 0, null, I_HOME, floors, floors, wall));
    }

    private static void ap(String name, int countries, int districts, int roof, int roofCol, int wall, int inside, int fmin, int fmax) {
        ALL_VARIANTS.add(new V(name, City.APARTMENT, countries, districts, roof, roofCol, 0, null, inside, fmin, fmax, wall));
    }

    static {
        // Shops found everywhere (or wherever the sign makes sense).
        s("Café", ALL, TOWN | PARK, R_STRIPES, 0xFF6E5A4A, 0xFF8A5A3A, "CAFÉ", I_CAFE);
        s("Bakery", EN, TOWN, R_FLAT, 0xFF9A8A70, 0xFFC8963A, "BAKERY", I_KITCHEN);
        s("Butcher", EN, TOWN, R_STRIPES, 0xFF8A6A60, 0xFFB03A2E, "BUTCHER", I_SHELVES);
        s("Florist", EN, TOWN, R_GREEN, 0xFF6E7A5A, 0xFFE07AA0, "FLOWERS", I_SHELVES);
        s("Bookshop", EN, MID | OLD | UNI, R_PARAPET, 0xFF5A5E66, 0xFF2E4F3A, "BOOKS", I_LIBRARY);
        s("Hardware store", EN, TOWN | IND, R_FLAT, 0xFF7C848A, 0xFFE0702E, "HARDWARE", I_SHELVES);
        s("Hair salon", ALL, TOWN, R_STRIPES, 0xFF7A6A72, 0xFFE07AA0, "SALON", I_SALON);
        s("Laundromat", EN, TOWN, R_FLAT, 0xFF8A9096, 0xFF3A8AE0, "LAUNDRY", I_LAUNDRY);
        s("Electronics store", ALL, DOWN | MID, R_PARAPET, 0xFF5A6670, 0xFF2E5FB0, "ELECTRONICS", I_SHELVES);
        s("Boutique", ALL, DOWN | MID | OLD, R_STRIPES, 0xFF6E5A7E, 0xFF2A2A2A, "BOUTIQUE", I_SHELVES);
        s("Shoe shop", EN, DOWN | MID, R_STRIPES, 0xFF7E7A5C, 0xFF6E3A2A, "SHOES", I_SHELVES);
        s("Toy shop", EN, MID | SUB, R_STRIPES, 0xFF8A6A3E, 0xFFE0C040, "TOYS", I_SHELVES);
        s("Pet shop", EN, MID | SUB, R_FLAT, 0xFF7A8A6A, 0xFF3FA860, "PETS", I_SHELVES);
        s("Bike shop", ALL, MID | OLD | UNI, R_FLAT, 0xFF6A7078, 0xFF3FB8B0, "BIKES", I_SHOWROOM);
        s("Bar", ALL, DOWN | MID | OLD, R_FLAT, 0xFF3A3A40, 0xFF8A2A4A, "BAR", I_BAR);
        s("Restaurant", EN, DOWN | MID | OLD, R_TILES, 0xFF8A4A32, 0xFF2A2A2A, "RESTAURANT", I_RESTAURANT);
        s("Pizzeria", ALL, TOWN, R_STRIPES, 0xFF8A5A3A, 0xFF2E8A3A, "PIZZA", I_RESTAURANT);
        s("Ice cream parlour", ALL, TOWN | PARK, R_STRIPES, 0xFFB8A8C8, 0xFFE07AA0, "GELATO", I_CAFE);
        s("Jeweller", EN, DOWN | MID, R_PARAPET, 0xFF5A5E66, 0xFFD8B040, "JEWELLER", I_SHELVES);
        s("Optician", EN, DOWN | MID, R_FLAT, 0xFF8A9096, 0xFF2E5FB0, "OPTICIAN", I_SALON);
        s("Dentist", ALL, MID | SUB, R_FLAT, 0xFFD8DCE0, 0xFF3FB8B0, "DENTIST", I_WARD);
        s("Gym", ALL, DOWN | MID | SUB, R_FLAT, 0xFF4A4E56, 0xFFE0702E, "GYM", I_GYM);
        s("Music shop", EN, MID | OLD | UNI, R_STRIPES, 0xFF4A3A4A, 0xFFD8B040, "MUSIC", I_SHELVES);
        s("Convenience store", EN | MX, TOWN, R_FLAT, 0xFFB8BCC0, 0xFFE0402E, "24/7", I_SHELVES);
        s("Arcade", US | JP, DOWN | MID, R_FLAT, 0xFF3A3A50, 0xFFE040C0, "ARCADE", I_ARCADE);

        // Public buildings and big offices.
        o("Post office", EN, MID | OLD | SUB, R_PARAPET, 0xFF8A6A4A, 0xFFB03A2E, "POST", I_OFFICE, 1, 2);
        o("Bank", ALL, DOWN | MID | OLD, R_PARAPET, 0xFF8A8680, 0xFF2E4F3A, "BANK", I_OFFICE, 2, 4);
        o("Library", ALL, MID | OLD | UNI, R_DOME, 0xFF8A8A84, 0xFF6E5A3A, "LIBRARY", I_LIBRARY, 2, 3);
        o("Cinema", ALL, DOWN | MID, R_FLAT, 0xFF3A3A40, 0xFFE03A3A, "CINEMA", I_SEATS, 2, 3);
        o("Museum", ALL, DOWN | OLD | PARK, R_DOME, 0xFF9C9488, 0xFF6E5A3A, "MUSEUM", I_LIBRARY, 2, 3);
        o("Hotel", ALL, DOWN | MID | OLD, R_POOL, 0xFF8A8E96, 0xFFD8B040, "HOTEL", I_HOTEL, 5, 14);
        o("Clinic", ALL, MID | SUB, R_FLAT, 0xFFE4E8EC, 0xFF2FA84F, "CLINIC", I_WARD, 1, 3);
        o("Town hall", EN, OLD | MID, R_CLOCK, 0xFF7A6A5A, 0xFFD8B040, "TOWN HALL", I_OFFICE, 2, 3);
        o("Courthouse", EN | MX, DOWN | OLD, R_DOME, 0xFF9C9488, 0xFF4A4E56, "COURT", I_SEATS, 2, 4);
        o("Theatre", ALL, DOWN | OLD, R_BARREL, 0xFF6A3A3A, 0xFFD8B040, "THEATRE", I_SEATS, 2, 3);
        o("Glass skyscraper", ALL, DOWN, R_GLASS, 0xFF5E7686, 0xFF9CC3D9, null, I_OFFICE, 18, 30);
        o("Office tower", ALL, DOWN | MID, R_HELIPAD, 0xFF6A6E74, 0xFFE8D24A, null, I_OFFICE, 12, 22);
        o("Bank headquarters", ALL, DOWN, R_ANTENNA, 0xFF4A4E56, 0xFF2E4F3A, "BANK", I_OFFICE, 16, 26);
        o("Hotel tower", ALL, DOWN, R_POOL, 0xFF7A7E86, 0xFFD8B040, "HOTEL", I_HOTEL, 14, 22);
        o("Department store", ALL, DOWN | MID, R_PARAPET, 0xFF8A8680, 0xFFB03A2E, "STORE", I_SHELVES, 4, 6);
        o("News tower", ALL, DOWN, R_ANTENNA, 0xFF5A5E66, 0xFFE03A3A, "NEWS", I_OFFICE, 12, 20);
        o("Convention centre", ALL, DOWN, R_BARREL, 0xFF9AA0A6, 0xFF2E5FB0, null, I_SEATS, 2, 3);
        o("Green-roof offices", ALL, DOWN | MID | UNI, R_GREEN, 0xFF6E7A5A, 0xFF3FA860, null, I_OFFICE, 4, 9);
        o("Solar offices", ALL, MID | SUB | IND, R_SOLAR, 0xFF8A8E94, 0xFF1F3A66, null, I_OFFICE, 3, 7);

        // Campus.
        o("Lecture hall", ALL, UNI, R_BARREL, 0xFF8A7A6A, 0xFF2E4F8A, "LECTURES", I_SEATS, 2, 3);
        o("University library", ALL, UNI, R_DOME, 0xFF8A8A84, 0xFF6E5A3A, "LIBRARY", I_LIBRARY, 3, 4);
        o("Science labs", ALL, UNI | IND, R_STACKS, 0xFF9AA0A6, 0xFF3FB8B0, "LABS", I_LAB, 3, 5);
        ap("Student halls", ALL, UNI, R_FLAT, 0xFF8A7A6A, 0xFFB86A48, I_DORM, 4, 7);
        o("Student union", ALL, UNI, R_TILES, 0xFF8A4A32, 0xFF2E4F8A, "UNION", I_CAFE, 2, 3);
        o("Sports hall", ALL, UNI | SUB, R_BARREL, 0xFF7C848A, 0xFFE0702E, "SPORTS", I_GYM, 2, 2);
        o("Observatory", ALL, UNI | PARK, R_DOME, 0xFFD8DCE0, 0xFF4A4E56, null, I_LAB, 2, 3);

        // Industry.
        wh("Factory", ALL, IND, R_SAWTOOTH, 0xFF8A9096, 0xFF9CC3D9, null, I_FACTORY);
        wh("Brewery", ALL, IND | OLD, R_TANKS, 0xFF8A6A4A, 0xFFD8B040, "BREWERY", I_FACTORY);
        wh("Cold storage", ALL, IND, R_FLAT, 0xFFE4E8EC, 0xFF3A8AE0, "COLD STORE", I_RACKS);
        wh("Recycling plant", ALL, IND, R_STACKS, 0xFF6F777D, 0xFF3FA860, "RECYCLING", I_FACTORY);
        wh("Steel works", ALL, IND, R_STACKS, 0xFF4A4E52, 0xFFE0702E, null, I_FACTORY);
        wh("Printing works", ALL, IND | MID, R_SAWTOOTH, 0xFF7C848A, 0xFF2A2A2A, "PRINT", I_FACTORY);
        wh("Bottling plant", ALL, IND, R_TANKS, 0xFF9AA0A4, 0xFF3A8AE0, null, I_FACTORY);
        wh("Data centre", ALL, IND | SUB, R_FLAT, 0xFF5A5E66, 0xFF3FB8B0, "DATA", I_RACKS);
        wh("Logistics hub", ALL, IND, R_FLAT, 0xFF8A9096, 0xFFE0C040, "LOGISTICS", I_RACKS);
        wh("Quonset store", US | AU, IND | PARK, R_BARREL, 0xFF9AA0A4, 0xFF7C848A, null, I_RACKS);
        wh("Self storage", EN, IND | SUB, R_PARAPET, 0xFFB8BCC0, 0xFFE0702E, "STORAGE", I_RACKS);
        wh("Car dealership", ALL, SUB | MID | IND, R_GLASS, 0xFF9CC3D9, 0xFFE03A3A, "CARS", I_SHOWROOM);
        wh("Big-box store", US | AU | MX, SUB | IND, R_PARAPET, 0xFFC8C4BC, 0xFF2E5FB0, "MEGA STORE", I_SHELVES);
        wh("Auto repair", ALL, IND | SUB | MID, R_SAWTOOTH, 0xFF7C7F82, 0xFFE0C040, "AUTO", I_SHOWROOM);
        wh("Bowling alley", US | AU | JP, SUB | MID, R_BARREL, 0xFF6A5A7A, 0xFFE03A3A, "BOWLING", I_GYM);

        // In the parks.
        o("Park café", ALL, PARK, R_HIP, 0xFF6E4A3A, 0xFF3FA860, "CAFÉ", I_CAFE, 1, 1);
        o("Glasshouse", ALL, PARK | UNI, R_GLASS, 0xFFA8D8B0, 0xFF3FA860, null, I_SHELVES, 1, 2);
        o("Gallery", ALL, PARK | OLD | MID, R_GLASS, 0xFFE8E8E8, 0xFF2A2A2A, "GALLERY", I_LIBRARY, 2, 3);

        // Homes everywhere.
        hs("Bungalow", ALL, R_HIP, 0xFF6E4A3A, 0xFFD8CBB0, 1);
        hs("Two-storey home", ALL, R_GABLE, 0xFF5A3D35, 0xFFE0D6C4, 2);
        hs("House with a pool", US | AU | MX, R_HOUSEPOOL, 0xFF8E3B2E, 0xFFE0D6C4, 1);
        hs("Solar house", ALL, R_SOLAR, 0xFF4E5560, 0xFFC9B79C, 2);
        hs("Duplex", EN | FR, R_GABLE, 0xFF6E4A3A, 0xFFB8A48A, 2);
        ap("Tower block", ALL, MID | SUB | DOWN, R_FLAT, 0xFF7C7F82, 0xFFB5A08A, I_HOME, 10, 16);
        ap("Low-rise flats", ALL, MID | SUB | OLD, R_PARAPET, 0xFF8A7A6A, 0xFFC9B8A0, I_HOME, 3, 4);
        ap("Courtyard block", ALL, MID | OLD | DOWN, R_COURTYARD, 0xFF7A6A5A, 0xFFD4C8B8, I_HOME, 4, 6);
        ap("Luxury flats", ALL, DOWN | MID, R_POOL, 0xFF6A6E74, 0xFFE2DCD0, I_HOME, 8, 14);

        // USA.
        s("Diner", US, TOWN | IND, R_STRIPES, 0xFFB8BCC0, 0xFFB83A30, "DINER", I_RESTAURANT);
        s("Burger drive-thru", US, SUB | MID | IND, R_STRIPES, 0xFFE0C040, 0xFFB83A30, "BURGERS", I_RESTAURANT);
        s("Donut shop", US, TOWN, R_STRIPES, 0xFFE07AA0, 0xFF8A5A3A, "DONUTS", I_CAFE);
        s("Pawn shop", US, MID | IND, R_FLAT, 0xFF6A6A60, 0xFFD8B040, "PAWN", I_SHELVES);
        s("Dollar store", US, SUB | MID, R_FLAT, 0xFF7A8A6A, 0xFF3FA860, "DOLLAR", I_SHELVES);
        s("Liquor store", US, MID | SUB, R_FLAT, 0xFF5A5E66, 0xFFE03A3A, "LIQUOR", I_SHELVES);
        o("Motel", US | AU, SUB | IND | PARK, R_FLAT, 0xFF8A6A4A, 0xFFE03A3A, "MOTEL", I_HOTEL, 1, 2);
        hs("Ranch house", US, R_HIP, 0xFF7A5A48, 0xFFC9B79C, 1);
        hs("Colonial house", US, R_GABLE, 0xFF4E5560, 0xFFF2EEE4, 2);
        hs("Craftsman bungalow", US, R_GABLE, 0xFF5A3D35, 0xFF8A9A7A, 1);

        // Australia.
        o("Corner pub", AU, MID | OLD | SUB, R_TILES, 0xFF7A2E28, 0xFF1F4F3A, "HOTEL", I_BAR, 2, 2);
        s("Fish and chip shop", AU, TOWN | PARK, R_STRIPES, 0xFF2E6A8A, 0xFFE0C040, "FISH & CHIPS", I_RESTAURANT);
        s("Milk bar", AU, SUB | MID, R_FLAT, 0xFFD8C8A0, 0xFF2E6A8A, "MILK BAR", I_SHELVES);
        s("Bottle shop", AU, SUB | MID, R_FLAT, 0xFF4C5048, 0xFFE03A3A, "BOTTLE-O", I_SHELVES);
        s("Newsagency", AU, TOWN, R_FLAT, 0xFF8A8E8A, 0xFFE0C040, "NEWSAGENCY", I_SHELVES);
        s("Op shop", AU, MID | SUB | OLD, R_STRIPES, 0xFF7C8C6A, 0xFF8A5A3A, "OP SHOP", I_SHELVES);
        s("Kebab shop", AU | FR, MID | DOWN, R_FLAT, 0xFF6A5A4A, 0xFFE0702E, "KEBABS", I_RESTAURANT);
        s("Surf shop", AU, SUB | PARK | MID, R_STRIPES, 0xFF3FB8B0, 0xFFE0C040, "SURF", I_SHELVES);
        o("RSL club", AU, SUB | MID, R_FLAT, 0xFF7A6A5A, 0xFF1F3F8A, "RSL", I_BAR, 1, 2);
        hs("Queenslander", AU, R_HIP, 0xFF8A9A8A, 0xFFEDEBE4, 1);
        hs("Federation house", AU, R_GABLE, 0xFF7A2E28, 0xFFA85A40, 1);
        hs("Weatherboard cottage", AU, R_GABLE, 0xFF4C5048, 0xFFB8C8D0, 1);
        wh("Hardware warehouse", AU, IND | SUB, R_PARAPET, 0xFFC8BCA2, 0xFF2E6A3A, "HARDWARE", I_RACKS);
        wh("Woolshed", AU, PARK, R_GABLE, 0xFF8A8E8A, 0xFFA85A40, null, I_RACKS);

        // Japan.
        s("Konbini", JP, TOWN | UNI, R_FLAT, 0xFFE8EAE4, 0xFF2E8A4A, "24H", I_SHELVES);
        s("Ramen shop", JP, TOWN | UNI, R_STRIPES, 0xFF3A3A40, 0xFFE03A3A, "RAMEN", I_RESTAURANT);
        s("Sushi bar", JP, DOWN | MID | OLD, R_TILES, 0xFF2E3640, 0xFFE8E2D2, "SUSHI", I_RESTAURANT);
        s("Izakaya", JP, DOWN | MID | OLD, R_TILES, 0xFF3A4656, 0xFFE03A3A, "IZAKAYA", I_BAR);
        o("Pachinko parlour", JP, DOWN | MID, R_FLAT, 0xFFE040C0, 0xFFE0C040, "PACHINKO", I_ARCADE, 2, 3);
        o("Karaoke box", JP, DOWN | MID, R_FLAT, 0xFF3A3A50, 0xFFE040C0, "KARAOKE", I_HOTEL, 3, 5);
        o("Capsule hotel", JP, DOWN, R_FLAT, 0xFF8A8E96, 0xFF3A8AE0, "CAPSULE", I_DORM, 6, 10);
        o("Sento bathhouse", JP, OLD | SUB, R_GABLE, 0xFF3A4656, 0xFF3A8AE0, "ゆ", I_BATH, 1, 1);
        o("Ryokan inn", JP, OLD | PARK, R_HIP, 0xFF2E3640, 0xFF6B4A3A, "RYOKAN", I_HOTEL, 2, 2);
        s("Manga café", JP, DOWN | MID | UNI, R_FLAT, 0xFF5A6670, 0xFFE0702E, "MANGA", I_LIBRARY);
        o("Game centre", JP, DOWN | MID, R_FLAT, 0xFF4A3A6A, 0xFFE040C0, "GAMES", I_ARCADE, 3, 4);
        s("100-yen shop", JP, TOWN, R_FLAT, 0xFFE8E4DC, 0xFFE03A3A, "100 YEN", I_SHELVES);
        s("Udon shop", JP, TOWN, R_TILES, 0xFF3A4656, 0xFFE8E2D2, "UDON", I_RESTAURANT);
        s("Drugstore", JP, TOWN, R_FLAT, 0xFFE8EAE4, 0xFFF0C040, "DRUG", I_SHELVES);
        s("Tea house", JP, OLD | PARK, R_HIP, 0xFF2E3640, 0xFF6B8A4A, "茶", I_CAFE);
        hs("Machiya townhouse", JP, R_TILES, 0xFF2E3640, 0xFF6A4A32, 2);
        hs("Modern box house", JP, R_FLAT, 0xFFC6C8C8, 0xFFEDE8DC, 2);
        ap("Danchi block", JP, SUB | MID, R_FLAT, 0xFFC8C4BC, 0xFFE8E4DC, I_HOME, 5, 5);
        ap("Mansion block", JP, MID | DOWN, R_PARAPET, 0xFFB8BCC0, 0xFFDCD2C0, I_HOME, 8, 12);

        // France.
        s("Boulangerie", FR, TOWN, R_STRIPES, 0xFF7A8490, 0xFFC8963A, "BOULANGERIE", I_KITCHEN);
        s("Pâtisserie", FR, DOWN | MID | OLD, R_STRIPES, 0xFF7A8490, 0xFFE07AA0, "PÂTISSERIE", I_CAFE);
        s("Brasserie", FR, DOWN | MID | OLD, R_STRIPES, 0xFF5E6874, 0xFFB03A2E, "BRASSERIE", I_RESTAURANT);
        s("Café-tabac", FR, TOWN, R_STRIPES, 0xFF6C7682, 0xFFB03A2E, "TABAC", I_BAR);
        s("Fromagerie", FR, MID | OLD, R_STRIPES, 0xFF7A8490, 0xFFE0C040, "FROMAGERIE", I_SHELVES);
        s("Boucherie", FR, TOWN, R_STRIPES, 0xFF7A8490, 0xFFB03A2E, "BOUCHERIE", I_SHELVES);
        s("Librairie", FR, MID | OLD | UNI, R_PARAPET, 0xFF5E6874, 0xFF2E4F3A, "LIBRAIRIE", I_LIBRARY);
        s("Fleuriste", FR, TOWN, R_GREEN, 0xFF7A8490, 0xFFE07AA0, "FLEURISTE", I_SHELVES);
        s("Caviste", FR, MID | OLD, R_STRIPES, 0xFF5E6874, 0xFF7A2A4A, "VINS", I_SHELVES);
        s("Crêperie", FR, OLD | MID | PARK, R_STRIPES, 0xFF7A8490, 0xFF2E4F8A, "CRÊPERIE", I_RESTAURANT);
        s("Chocolatier", FR, DOWN | OLD, R_STRIPES, 0xFF5E6874, 0xFF6E3A2A, "CHOCOLAT", I_SHELVES);
        s("Pressing", FR, MID | SUB, R_FLAT, 0xFF8A9298, 0xFF3A8AE0, "PRESSING", I_LAUNDRY);
        s("Épicerie", FR, TOWN, R_STRIPES, 0xFF7A8490, 0xFF2E6A4A, "ÉPICERIE", I_SHELVES);
        o("Mairie", FR, OLD | MID, R_CLOCK, 0xFF5E6874, 0xFF2E4F8A, "MAIRIE", I_OFFICE, 2, 3);
        o("Bureau de poste", FR, MID | OLD | SUB, R_PARAPET, 0xFF7A8490, 0xFFF0C040, "LA POSTE", I_OFFICE, 1, 2);
        o("Hôtel particulier", FR, OLD | DOWN, R_MANSARD, 0xFF5E6874, 0xFFD8B040, null, I_HOTEL, 3, 4);
        hs("Maison de ville", FR, R_GABLE, 0xFF4E5560, 0xFFE8DEC8, 2);
        hs("Provençal mas", FR, R_TILES, 0xFFB0623E, 0xFFE2D6BE, 1);
        ap("Haussmann block", FR, DOWN | MID | OLD, R_MANSARD, 0xFF7A8490, 0xFFE8DEC8, I_HOME, 6, 7);

        // Mexico.
        s("Taquería", MX, TOWN | IND, R_STRIPES, 0xFFC9BFAE, 0xFFE8504A, "TACOS", I_RESTAURANT);
        s("Tortillería", MX, TOWN, R_FLAT, 0xFFC9BFAE, 0xFFF0C04A, "TORTILLAS", I_KITCHEN);
        s("Panadería", MX, TOWN, R_FLAT, 0xFFD2C6B2, 0xFFC8963A, "PANADERÍA", I_KITCHEN);
        s("Tiendita", MX, TOWN, R_FLAT, 0xFFC9BFAE, 0xFFE8504A, "ABARROTES", I_SHELVES);
        s("Papelería", MX, MID | SUB | UNI, R_FLAT, 0xFFD2C6B2, 0xFF2E5FB0, "PAPELERÍA", I_SHELVES);
        s("Ferretería", MX, TOWN | IND, R_FLAT, 0xFFB8AE9C, 0xFFE0702E, "FERRETERÍA", I_SHELVES);
        s("Cantina", MX, MID | OLD, R_TILES, 0xFFB4583A, 0xFF2E6A3A, "CANTINA", I_BAR);
        s("Paletería", MX, TOWN | PARK, R_STRIPES, 0xFFE07A8C, 0xFF4FB8B0, "PALETAS", I_CAFE);
        s("Lavandería", MX, MID | SUB, R_FLAT, 0xFFD2C6B2, 0xFF3A8AE0, "LAVANDERÍA", I_LAUNDRY);
        s("Estética", MX, TOWN, R_STRIPES, 0xFFE07A8C, 0xFF7A5AC0, "ESTÉTICA", I_SALON);
        s("Cocina económica", MX, TOWN | IND, R_FLAT, 0xFFC9BFAE, 0xFFE8873A, "COMIDA", I_RESTAURANT);
        s("Mini súper", MX, TOWN, R_FLAT, 0xFFE8E4DC, 0xFFE0402E, "MINI SÚPER", I_SHELVES);
        s("Juguería", MX, TOWN | PARK, R_STRIPES, 0xFF3FA860, 0xFFF0C04A, "JUGOS", I_CAFE);
        s("Zapatería", MX, DOWN | MID, R_FLAT, 0xFFD2C6B2, 0xFF6E3A2A, "ZAPATOS", I_SHELVES);
        hs("Casa colonial", MX, R_COURTYARD, 0xFFB4583A, 0xFFF0C04A, 1);
        hs("Casa de interés social", MX, R_FLAT, 0xFFC9BFAE, 0xFF4FB8B0, 1);
        hs("Casa de dos pisos", MX, R_FLAT, 0xFFD2C6B2, 0xFFE07A8C, 2);
        ap("Vecindad", MX, OLD | MID, R_COURTYARD, 0xFFB4583A, 0xFFE8873A, I_HOME, 2, 3);
        wh("Mercado municipal", MX, MID | OLD, R_BARREL, 0xFFC9BFAE, 0xFFE8504A, "MERCADO", I_SHELVES);

        // (Everything above was in the game before 10.1; the city generator picks from it exactly as it
        // always did, so saved cities rebuild the same. What follows is swapped in afterwards, see swap().)
        LEGACY = ALL_VARIANTS.size();

        // More of each country's own.
        // USA.
        s("Barber shop", US, TOWN, R_STRIPES, 0xFF8A3A3A, 0xFF2E5FB0, "BARBER", I_SALON);
        s("Tattoo parlor", US, MID | DOWN, R_FLAT, 0xFF2A2A30, 0xFFE03A3A, "TATTOO", I_SALON);
        s("Sports bar", US, DOWN | MID | SUB, R_FLAT, 0xFF3A3A40, 0xFF2E8A3A, "SPORTS BAR", I_BAR);
        s("Bail bonds", US, DOWN | MID, R_FLAT, 0xFF6A6A60, 0xFFF0C040, "BAIL BONDS", I_OFFICE);
        s("BBQ smokehouse", US, SUB | IND | MID, R_GABLE, 0xFF5A3A2A, 0xFFE0702E, "BBQ", I_RESTAURANT);
        s("Mattress store", US, SUB | MID, R_PARAPET, 0xFFC8C4BC, 0xFF2E5FB0, "MATTRESSES", I_SHOWROOM);
        o("Megachurch", US, SUB | MID, R_BARREL, 0xFFE4E0D8, 0xFF2E5FB0, "CHURCH", I_PEWS, 1, 2);
        wh("Auto parts store", US, IND | SUB, R_PARAPET, 0xFFB8BCC0, 0xFFE03A3A, "AUTO PARTS", I_SHELVES);
        wh("Grain elevator", US, PARK | IND, R_TANKS, 0xFFB8B4A8, 0xFF8A6A4A, null, I_RACKS);
        hs("Trailer home", US, R_FLAT, 0xFFD8DCE0, 0xFF9AA8B4, 1);
        ap("Brownstone", US, OLD | MID, R_PARAPET, 0xFF6A4A3A, 0xFF8A5A40, I_HOME, 4, 5);

        // Australia.
        s("Pie shop", AU, TOWN, R_STRIPES, 0xFF8A6A3E, 0xFFC8963A, "PIES", I_KITCHEN);
        s("Chemist", AU, TOWN, R_FLAT, 0xFFE4E8EC, 0xFF2FA84F, "CHEMIST", I_SHELVES);
        s("TAB", AU, MID | SUB, R_FLAT, 0xFF2E5A3A, 0xFFE0C040, "TAB", I_ARCADE);
        s("Chinese takeaway", AU, SUB | MID, R_STRIPES, 0xFFB03A2E, 0xFFE0C040, "TAKEAWAY", I_RESTAURANT);
        o("Bowls club", AU, PARK | SUB, R_FLAT, 0xFFE4E0D8, 0xFF2E6A3A, "BOWLS", I_BAR, 1, 1);
        o("Surf life saving club", AU, PARK | SUB, R_FLAT, 0xFFE0C040, 0xFFE03A3A, "SURF CLUB", I_GYM, 2, 2);
        wh("Ute dealer", AU, IND | SUB, R_GLASS, 0xFF9CC3D9, 0xFF2E6A3A, "UTES", I_SHOWROOM);
        hs("Terrace house", AU, R_TILES, 0xFF7A2E28, 0xFFE2D6C4, 2);
        hs("Fibro shack", AU, R_FLAT, 0xFFC8CCC4, 0xFFE8E4D8, 1);
        ap("Walk-up flats", AU, MID | SUB, R_TILES, 0xFF8A4A32, 0xFFC9A888, I_HOME, 3, 3);

        // Japan.
        o("Shinto shrine", JP, OLD | PARK | SUB, R_PAGODA, 0xFF3A4656, 0xFFE03A2E, "神社", I_PEWS, 1, 1);
        o("Buddhist temple", JP, OLD | PARK, R_PAGODA, 0xFF2E3640, 0xFFD8B040, "寺", I_PEWS, 1, 2);
        s("Yakitori stand", JP, TOWN, R_STRIPES, 0xFF3A3A40, 0xFFE0702E, "YAKITORI", I_BAR);
        s("Okonomiyaki shop", JP, TOWN, R_TILES, 0xFF3A4656, 0xFFE0C040, "OKONOMIYAKI", I_RESTAURANT);
        s("Tatami shop", JP, OLD | MID, R_TILES, 0xFF2E3640, 0xFF6B8A4A, "TATAMI", I_SHELVES);
        o("Onsen", JP, OLD | PARK, R_HIP, 0xFF2E3640, 0xFF3A8AE0, "♨", I_BATH, 1, 2);
        o("Cram school", JP, MID | SUB | UNI, R_FLAT, 0xFFE8EAE4, 0xFF2E5FB0, "JUKU", I_CLASSROOM, 3, 5);
        wh("Batting centre", JP, SUB | MID, R_BARREL, 0xFF6A7078, 0xFF3FA860, "BATTING", I_GYM);
        hs("Gasshō farmhouse", JP, R_GABLE, 0xFF7A6A4A, 0xFF5A4A3A, 2);
        ap("Apāto block", JP, SUB | MID, R_FLAT, 0xFFD8D4CC, 0xFFE8E4DC, I_HOME, 2, 2);

        // France.
        s("Pharmacie", FR, TOWN, R_FLAT, 0xFF7A8490, 0xFF2FA84F, "PHARMACIE", I_SHELVES);
        s("Bistrot", FR, DOWN | MID | OLD, R_STRIPES, 0xFF5E6874, 0xFF7A2A4A, "BISTROT", I_RESTAURANT);
        s("Salon de thé", FR, OLD | MID | PARK, R_STRIPES, 0xFF7A8490, 0xFF6B8A4A, "SALON DE THÉ", I_CAFE);
        o("Château", FR, PARK | OLD, R_MANSARD, 0xFF4E5560, 0xFFD8B040, "CHÂTEAU", I_HOTEL, 3, 3);
        o("Boulodrome", FR, PARK | SUB, R_FLAT, 0xFFD8C8A0, 0xFF2E4F8A, "PÉTANQUE", I_BAR, 1, 1);
        wh("Marché couvert", FR, MID | OLD, R_BARREL, 0xFF7A8490, 0xFF2E6A4A, "MARCHÉ", I_SHELVES);
        wh("Cave coopérative", FR, IND | PARK, R_TANKS, 0xFFB0623E, 0xFF7A2A4A, "CAVE", I_RACKS);
        hs("Maison bourgeoise", FR, R_MANSARD, 0xFF4E5560, 0xFFE8DEC8, 3);
        hs("Longère", FR, R_GABLE, 0xFF3A4048, 0xFFD8CCB4, 1);
        ap("HLM block", FR, SUB | MID, R_FLAT, 0xFFB8BCC0, 0xFFE0D8C8, I_HOME, 10, 15);

        // Mexico.
        s("Farmacia", MX, TOWN, R_FLAT, 0xFFE4E8EC, 0xFF2E6AB0, "FARMACIA", I_SHELVES);
        s("Carnicería", MX, TOWN, R_FLAT, 0xFFC9BFAE, 0xFFB03A2E, "CARNICERÍA", I_SHELVES);
        s("Birriería", MX, TOWN | IND, R_STRIPES, 0xFFB4583A, 0xFFF0C04A, "BIRRIA", I_RESTAURANT);
        s("Pulquería", MX, OLD | MID, R_TILES, 0xFF4FB8B0, 0xFFE8504A, "PULQUE", I_BAR);
        s("Mezcalería", MX, DOWN | OLD, R_TILES, 0xFF6A3A5A, 0xFFF0C04A, "MEZCAL", I_BAR);
        o("Arena de lucha libre", MX, DOWN | MID, R_BARREL, 0xFF3A3A50, 0xFFE040C0, "LUCHA LIBRE", I_SEATS, 2, 3);
        o("Hacienda", MX, PARK | OLD, R_COURTYARD, 0xFFB4583A, 0xFFF0C04A, "HACIENDA", I_HOTEL, 1, 2);
        wh("Mercado de artesanías", MX, OLD | MID | PARK, R_TILES, 0xFFC9BFAE, 0xFF4FB8B0, "ARTESANÍAS", I_SHELVES);
        hs("Casa de adobe", MX, R_FLAT, 0xFFB89A78, 0xFFC8A47A, 1);
        ap("Unidad habitacional", MX, SUB | MID, R_FLAT, 0xFFD2C6B2, 0xFFE8873A, I_HOME, 5, 5);

        // And more for each kind of place (every country): downtown, old town, parkland, industry, suburbs, campus.
        o("Stock exchange", ALL, DOWN, R_DOME, 0xFF9C9488, 0xFF2E4F3A, "EXCHANGE", I_OFFICE, 4, 6);
        o("Concert hall", ALL, DOWN | OLD, R_BARREL, 0xFF8A8680, 0xFFD8B040, "CONCERTS", I_SEATS, 3, 4);
        s("Antique shop", ALL, OLD, R_STRIPES, 0xFF6A5A4A, 0xFFD8B040, "ANTIQUES", I_SHELVES);
        o("Clock tower", ALL, OLD, R_CLOCK, 0xFF7A6A5A, 0xFFD8B040, null, I_OFFICE, 4, 5);
        o("Boathouse", ALL, PARK, R_GABLE, 0xFF5A4A3A, 0xFF2E6A8A, "BOATS", I_RACKS, 1, 1);
        wh("Garden centre", ALL, PARK | SUB, R_GLASS, 0xFFA8D8B0, 0xFF3FA860, "GARDEN", I_SHELVES);
        wh("Container depot", ALL, IND, R_STRIPES, 0xFF8A9096, 0xFFE0702E, "CONTAINERS", I_RACKS);
        wh("Lumber yard", ALL, IND | PARK, R_GABLE, 0xFF8A6A4A, 0xFFC8963A, "LUMBER", I_RACKS);
        wh("Cement works", ALL, IND, R_TANKS, 0xFFB8B4AC, 0xFF6F777D, null, I_FACTORY);
        o("Community centre", ALL, SUB | MID, R_FLAT, 0xFF8A8E94, 0xFF3FA860, "COMMUNITY", I_GYM, 1, 2);
        s("Vet clinic", ALL, SUB | MID, R_FLAT, 0xFFE4E8EC, 0xFF3FB8B0, "VET", I_WARD);
        o("Research institute", ALL, UNI | IND, R_SOLAR, 0xFF8A8E94, 0xFF3FB8B0, "INSTITUTE", I_LAB, 3, 6);
        s("Student bar", ALL, UNI, R_FLAT, 0xFF3A3A40, 0xFFE0C040, "BAR", I_BAR);
    }

    /**
     * Picks a variant for a new lot, or -1 to keep the plain building. Big lots get the big buildings,
     * small lots the small ones.
     */
    static int pick(int kind, int countryId, int district, int lw, int lh, Random rnd) {
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
        int cbit = 1 << countryId, dbit = 1 << Math.max(0, Math.min(6, district));
        int area = lw * lh, n = 0, pickN = -1;
        for (int i = 0; i < LEGACY; i++) {
            V v = ALL_VARIANTS.get(i);
            if (v.base != kind || (v.countries & cbit) == 0 || (v.districts & dbit) == 0) continue;
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
    static int swap(int picked, int kind, int countryId, int district, int lw, int lh) {
        if (picked < 0 && extra.nextFloat() > 0.5f) return picked;
        int cbit = 1 << countryId, dbit = 1 << Math.max(0, Math.min(6, district));
        int area = lw * lh, older = 0, newer = 0, pickN = -1;
        for (int i = 0; i < ALL_VARIANTS.size(); i++) {
            V v = ALL_VARIANTS.get(i);
            if (v.base != kind || (v.countries & cbit) == 0 || (v.districts & dbit) == 0) continue;
            if (v.floorsMin >= 12 && area < 64) continue;
            if ((v.roof == R_BARREL || v.roof == R_COURTYARD || v.roof == R_DOME || v.roof == R_PAGODA) && area < 36) continue;
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
