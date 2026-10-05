package com.instantaxtion.zombiesandbox;

import java.util.Random;

/**
 * Where the city is: the country sets the look of its buildings, its street, town and people's names, the
 * road markings, the police cars and which side of the road traffic keeps to.
 */
final class Country {
    static final int USA = 0, AUSTRALIA = 1, JAPAN = 2, FRANCE = 3, MEXICO = 4, SWITZERLAND = 5, KOREA = 6,
            MALAYSIA = 7, PORTUGAL = 8, MOROCCO = 9, RUSSIA = 10;
    static final String[] NAMES = {"USA", "Australia", "Japan", "France", "Mexico", "Switzerland", "South Korea",
            "Malaysia", "Portugal", "Morocco", "Russia"};
    static final String[] INFO = {
            "Wide avenues with yellow centre lines, clapboard houses and black-and-white cruisers.",
            "Brick and weatherboard homes under tile and Colorbond roofs, dry grass, chequered police cars. Traffic drives on the left.",
            "Pale walls and blue-grey tiled roofs, temples and shrines, black-and-white patrol cars. Traffic drives on the left.",
            "Cream stone and zinc roofs, rues and boulevards, white and blue police cars.",
            "Bright painted walls and flat roofs, calles and avenidas, blue and white patrol cars.",
            "Chalets and steep roofs, tidy old towns, Strassen and Gassen, white and orange police cars.",
            "Blue and green tiled roofs, white apartment towers, -ro and -gil streets, blue and white patrol cars.",
            "Tin-roofed houses on stilts, shophouses and mosques, Jalan everything, blue and white police cars. Traffic drives on the left.",
            "White walls and terracotta roofs, tiled facades, ruas and avenidas, dark blue police cars.",
            "Flat-roofed whitewashed and ochre houses, medinas and mosques, derbs and avenues, grey-green police cars.",
            "Pastel blocks and dachas, onion-domed churches, ulitsas and prospekts, white and blue police cars."};

    /** The country of the game being played (for people's names). */
    static volatile Country current;

    final int id;
    /**
     * Which of the five original looks its buildings, cars and facades are drawn in (the newer countries
     * borrow the nearest), and how its places of worship look (a country of its own, or -1 for a plain church).
     */
    int look, churchLook = -1;
    /** The highway route shield's colour. */
    int shieldColor = 0xFF1E6A3A;
    /** Traffic keeps to the left. */
    final boolean leftHand;
    /** Colour of the centre line on ordinary streets. */
    final int centreLine;
    final int[] houseRoofs, houseWalls, shopRoofs, shopWalls, apartmentWalls;
    /** Police cars: body colour, door panels, a contrasting roof (0 for none), and a chequered band down the sides. */
    final int cruiserBody, cruiserDoor, cruiserRoof;
    final boolean cruiserChecks;
    /** The two colours of the chequered band. */
    int checkA, checkB;
    final String[] streets, mainWords, localWords;
    /** The street type comes before the name ("Rue de la Paix") rather than after ("Oak St"). */
    final boolean typeFirst;
    final String ringRoad;
    final String[] townStart, townEnd, districtWords, hamletEnds;
    final String[] churches, schools, markets, pharmacies, gunStores, bases;
    final String hospital, policeStation, fireStation;
    final String[] first, last, dogs;
    /** Pavement, grass and plaza colours (0 keeps the usual), and roofs/walls for offices and old buildings (null keeps the usual). */
    int pavement, grass, plaza;
    /** The everyday traffic: a weighted list of Fleet car models, their paint, and the local taxis and buses. */
    int[] carModels, carColors;
    int taxiColor, busColor;
    int[] officeRoofs, officeWalls, oldWalls, oldRoofs;

    private static final Country[] ALL = new Country[NAMES.length];

    static Country get(int id) {
        // (The newer countries are added after the original five.)
        id = Math.max(0, Math.min(NAMES.length - 1, id));
        synchronized (ALL) {
            if (ALL[id] == null) ALL[id] = new Country(id);
            return ALL[id];
        }
    }

    /** A street name: the type of street before or after the name, as the country does it. */
    String streetName(String base, boolean main, Random rnd) {
        String[] types = main ? mainWords : localWords;
        String type = types[rnd.nextInt(types.length)];
        if (type.isEmpty()) return base;
        if (typeFirst) return type + " " + base;
        // ("Sakura-dori", "Bahnhofstrasse", "Lenina Ulitsa": joined on; "Oak St" with a space.)
        boolean join = id == JAPAN || type.charAt(0) == '-' || type.charAt(0) == ' ' || Character.isLowerCase(type.charAt(0));
        return join ? base + type : base + " " + type;
    }

    /** A sign on a roof in the local language. */
    String sign(String english) {
        String[][] words;
        switch (id) {
            case FRANCE: words = new String[][]{{"FIRE", "POMPIERS"}, {"MARKET", "MARCHÉ"}, {"SCHOOL", "ÉCOLE"}, {"GAS", "ESSENCE"}}; break;
            case MEXICO: words = new String[][]{{"POLICE", "POLICÍA"}, {"FIRE", "BOMBEROS"}, {"MARKET", "MERCADO"}, {"SCHOOL", "ESCUELA"}, {"GAS", "GASOLINA"}}; break;
            case AUSTRALIA: words = new String[][]{{"GAS", "PETROL"}}; break;
            case JAPAN: words = new String[][]{{"POLICE", "KOBAN"}}; break;
            case SWITZERLAND: words = new String[][]{{"POLICE", "POLIZEI"}, {"FIRE", "FEUERWEHR"}, {"MARKET", "MIGROS"}, {"SCHOOL", "SCHULE"}, {"GAS", "TANKSTELLE"}}; break;
            case KOREA: words = new String[][]{{"POLICE", "경찰"}, {"FIRE", "소방서"}, {"MARKET", "마트"}, {"SCHOOL", "학교"}, {"GAS", "주유소"}}; break;
            case MALAYSIA: words = new String[][]{{"POLICE", "POLIS"}, {"FIRE", "BOMBA"}, {"MARKET", "PASAR"}, {"SCHOOL", "SEKOLAH"}, {"GAS", "PETROL"}}; break;
            case PORTUGAL: words = new String[][]{{"POLICE", "POLÍCIA"}, {"FIRE", "BOMBEIROS"}, {"MARKET", "MERCADO"}, {"SCHOOL", "ESCOLA"}, {"GAS", "COMBUSTÍVEL"}}; break;
            case MOROCCO: words = new String[][]{{"POLICE", "POLICE"}, {"FIRE", "POMPIERS"}, {"MARKET", "SOUK"}, {"SCHOOL", "ÉCOLE"}, {"GAS", "STATION"}}; break;
            case RUSSIA: words = new String[][]{{"POLICE", "ПОЛИЦИЯ"}, {"FIRE", "ПОЖАРНАЯ"}, {"MARKET", "РЫНОК"}, {"SCHOOL", "ШКОЛА"}, {"GAS", "АЗС"}}; break;
            default: return english;
        }
        for (String[] w : words) if (w[0].equals(english)) return w[1];
        return english;
    }

    String townName(Random r) {
        String a = townStart[r.nextInt(townStart.length)], b = townEnd[r.nextInt(townEnd.length)];
        // No "Champchamp".
        if (a.toLowerCase().contains(b.trim().toLowerCase()) || b.toLowerCase().contains(a.trim().toLowerCase()))
            b = townEnd[(java.util.Arrays.asList(townEnd).indexOf(b) + 1) % townEnd.length];
        return a + b;
    }

    /**
     * A neighbourhood's name in the local style: French quartiers and Mexican colonias don't have English
     * endings. The type is a City district type, -1 for a plain one or -2 for the countryside.
     */
    String districtName(String english, String word, int type) {
        if (id == FRANCE) {
            switch (type) {
                case 0: return "Centre-Ville";
                case 2: return "Vieille Ville";
                case 4: return "Zone Industrielle " + word;
                case 5: return "Quartier Universitaire";
                case -2: return "Campagne de " + word;
                default: return word;
            }
        }
        if (id == MEXICO) {
            switch (type) {
                case 0: return "Centro";
                case 2: return "Centro Histórico";
                case 4: return "Parque Industrial " + word;
                case 5: return "Ciudad Universitaria";
                case -2: return "Valle de " + word;
                default: return "Colonia " + word;
            }
        }
        if (id == JAPAN && type == 0) return "Chuo-ku";
        if (id == KOREA && type == 0) return "Jung-gu";
        if (id == SWITZERLAND && type == 2) return "Altstadt";
        if (id == PORTUGAL) {
            switch (type) {
                case 0: return "Baixa";
                case 2: return "Centro Histórico";
                case 4: return "Zona Industrial " + word;
                case -2: return "Campo de " + word;
                default: return "Bairro " + word;
            }
        }
        if (id == MOROCCO) {
            switch (type) {
                case 0: return "Ville Nouvelle";
                case 2: return "Médina";
                case 4: return "Zone Industrielle " + word;
                case -2: return "Douar " + word;
                default: return "Hay " + word;
            }
        }
        if (id == RUSSIA) {
            switch (type) {
                case 0: return "Tsentr";
                case 2: return "Stary Gorod";
                case 4: return "Promzona " + word;
                case -2: return word + " Selo";
                default: return "Mikrorayon " + word;
            }
        }
        if (id == MALAYSIA && type != -2 && type != 0 && type != 2) return "Taman " + word;
        return english;
    }

    private Country(int id) {
        this.id = id;
        switch (id) {
            case AUSTRALIA:
                leftHand = true;
                centreLine = 0xFFE4E4E0;
                houseRoofs = new int[]{0xFFB0583A, 0xFF4C5048, 0xFF3A4A5A, 0xFFC8BCA2, 0xFF7A2E28, 0xFF7C8C6A};
                houseWalls = new int[]{0xFFD8C8A0, 0xFFA85A40, 0xFFEDEBE4, 0xFFB8C8D0, 0xFFD6B880, 0xFFB0ACA4};
                shopRoofs = new int[]{0xFF4C5048, 0xFFC8BCA2, 0xFF3A4A5A, 0xFF8A8E8A, 0xFF7C8C6A, 0xFFB8B4AA};
                shopWalls = new int[]{0xFFEDEBE4, 0xFF2E5A7A, 0xFFD6B880, 0xFF9A3A2E, 0xFF5A7A4A, 0xFFE8D8B0};
                apartmentWalls = new int[]{0xFFD6B880, 0xFFE8E2D4, 0xFFC8BCA8, 0xFFA85A40, 0xFFD8C8A0};
                cruiserBody = 0xFFF2F2F2;
                cruiserDoor = 0xFFF2F2F2;
                cruiserRoof = 0;
                cruiserChecks = true;
                checkA = 0xFF1F3F8A;
                checkB = 0xFFF2F2F2;
                streets = new String[]{"George", "Elizabeth", "Macquarie", "Collins", "Bourke", "Flinders", "Swanston",
                        "Hunter", "Pitt", "Kent", "Sussex", "Victoria", "Albert", "Queen", "King", "William", "Murray",
                        "Darling", "Wattle", "Banksia", "Jacaranda", "Waratah", "Bottlebrush", "Acacia", "Bay", "Beach",
                        "Ocean", "Harbour", "Station", "Church", "High", "Railway", "Anzac", "Lawson", "Paterson",
                        "Kingsford Smith", "Cook", "Phillip", "Hume", "Sturt", "Burke", "Wills", "Bligh", "Lonsdale",
                        "Spencer", "Gouger", "Grenfell", "Ironbark", "Boronia", "Kurrajong"};
                mainWords = new String[]{"Road", "Highway", "Parade", "Avenue", "Esplanade"};
                localWords = new String[]{"Street", "Street", "Road", "Crescent", "Close", "Court", "Place", "Lane",
                        "Terrace", "Grove"};
                typeFirst = false;
                ringRoad = "Ring Road";
                townStart = new String[]{"Wood", "Bay", "Black", "Glen", "Wattle", "Gum", "Red", "Sandy", "Broad",
                        "Port ", "Mount ", "Warra", "Coola", "Bonda", "Yarra", "Kurra", "Mulla", "Wilga", "Narra", "Tumba"};
                townEnd = new String[]{"ville", " Creek", " Springs", "dale", "gong", "bah", " Heads", " Downs",
                        " Point", "wood", "bri", "jong", " Valley", " Bay"};
                districtWords = new String[]{"Wattle", "Banksia", "Gum", "Bay", "North", "South", "East", "West",
                        "Harbour", "Ridge", "Creek", "Surf", "Red Hill", "Kings", "Glen", "Range", "Waratah", "Ironbark",
                        "Jacaranda", "Point"};
                hamletEnds = new String[]{" Creek", " Station", " Crossing", " Flat", " Gully", " Siding", " Well", " Bore"};
                churches = new String[]{"St Mary's Cathedral", "St Patrick's Church", "Uniting Church", "St John's Anglican",
                        "St Francis Xavier", "Holy Spirit Church", "Scots Church", "St Paul's Church"};
                schools = new String[]{"Bayside High", "Wattle Park Primary", "St Joseph's College", "Southern Cross High",
                        "Gum Tree Primary", "Riverside Secondary College"};
                markets = new String[]{"Wollies", "Coalz", "IGA Local", "FoodWorks", "Aldy", "Corner Deli"};
                pharmacies = new String[]{"Discount Chemist", "Corner Chemist", "Coastal Pharmacy", "Main Street Chemist"};
                gunStores = new String[]{"Outback Firearms", "Bushman's Guns", "Southern Cross Sporting", "Stockman's Arms"};
                bases = new String[]{"Lavarack Barracks", "Holsworthy Barracks", "Robertson Barracks", "Gallipoli Barracks",
                        "Puckapunyal", "Enoggera Barracks"};
                hospital = "Base Hospital";
                policeStation = "Police Station ";
                fireStation = "Fire Station ";
                first = new String[]{"Oliver", "Charlotte", "Jack", "Mia", "William", "Olivia", "Noah", "Amelia", "Lachlan",
                        "Chloe", "Cooper", "Matilda", "Harrison", "Isla", "Riley", "Ruby", "Mitchell", "Zoe", "Jake",
                        "Sienna", "Bailey", "Ella", "Liam", "Grace", "Kai", "Lily", "Ethan", "Jessica", "Tom", "Kylie"};
                last = new String[]{"Smith", "Jones", "Williams", "Brown", "Wilson", "Taylor", "Nguyen", "Johnson",
                        "Martin", "White", "Anderson", "Walker", "Thompson", "Ryan", "Kelly", "Murphy", "O'Brien",
                        "Campbell", "Harris", "Lee", "King", "Wright", "Chen", "Singh", "Robinson", "Clarke"};
                dogs = new String[]{"Bluey", "Rusty", "Max", "Bella", "Charlie", "Ruby", "Digger", "Molly", "Banjo",
                        "Kelpie", "Coco", "Rex", "Sheila", "Ziggy", "Bingo", "Tex"};
                carModels = new int[]{0, 0, 1, 1, 1, 2, 2, 2, 4, 4, 6, 7};
                carColors = new int[]{0xFFF2F2F2, 0xFFF2F2F2, 0xFF9AA0A6, 0xFF2A2C30, 0xFF1F4F8A, 0xFFB03A2E,
                        0xFF6E7A5A, 0xFFD8C8A0};
                taxiColor = 0xFFF2C21A;
                busColor = 0xFFE8E8E8;
                pavement = 0xFF9E9A90;
                grass = 0xFF6E7A42;
                officeRoofs = new int[]{0xFFB8B4AA, 0xFF8A8E8A, 0xFFC8C4BA, 0xFF4C5048, 0xFFA8ACA6, 0xFF9AA0A0};
                officeWalls = new int[]{0xFFE8E2D4, 0xFFD6B880, 0xFFC8C8C4, 0xFF8AA4B4, 0xFFD8C8A0};
                oldWalls = new int[]{0xFFD6B880, 0xFFC8A670, 0xFFA85A40, 0xFFE0CFA4, 0xFFB8925C};
                oldRoofs = new int[]{0xFF4C5048, 0xFF7A2E28, 0xFF8A8E8A, 0xFFB0583A};
                break;
            case JAPAN:
                leftHand = true;
                centreLine = 0xFFE8E8E8;
                houseRoofs = new int[]{0xFF3A4656, 0xFF2E3640, 0xFF56606A, 0xFF4A5A70, 0xFF6B4A3A, 0xFF3F5A68};
                houseWalls = new int[]{0xFFEDE8DC, 0xFFDCD6C8, 0xFFE8E2D2, 0xFFC9C2B4, 0xFFF2EEE6, 0xFFB8B0A0};
                shopRoofs = new int[]{0xFF8A9096, 0xFF6C747C, 0xFF9AA2AA, 0xFF5A6670, 0xFF7C848C, 0xFF4E5A66};
                shopWalls = new int[]{0xFFF2F0EA, 0xFFE0DCD4, 0xFFD02A2A, 0xFF2A5AA8, 0xFFF0D040, 0xFFE8E4DC};
                apartmentWalls = new int[]{0xFFE8E4DC, 0xFFD4D0C8, 0xFFC8C4BC, 0xFFDCD2C0, 0xFFB8B4B0};
                cruiserBody = 0xFF141414;
                cruiserDoor = 0xFF141414;
                cruiserRoof = 0xFFF2F2F2;
                cruiserChecks = false;
                streets = new String[]{"Sakura", "Chuo", "Kita", "Minami", "Higashi", "Nishi", "Hana", "Matsu",
                        "Ume", "Yama", "Kawa", "Hon", "Shin", "Asahi", "Aoba", "Kasuga", "Hikari", "Sakae", "Fuji",
                        "Momiji", "Tsubaki", "Heiwa", "Midori", "Ekimae", "Kotobuki", "Wakaba", "Hibari", "Tachibana",
                        "Suzuran", "Yayoi", "Showa", "Meiji", "Taisho", "Kinuta", "Sumire", "Kiku", "Hinode", "Kaede",
                        "Nishiki", "Oyama", "Ginza", "Daimon", "Tenjin", "Miyuki", "Kyomachi", "Hamamatsu", "Kanda",
                        "Shirakawa", "Nakano", "Kurenai"};
                mainWords = new String[]{"-dori", "-dori", "-kaido", " Avenue"};
                localWords = new String[]{"-cho", "-michi", "-cho", "-dori", "-yokocho", "-zaka"};
                typeFirst = false;
                ringRoad = "Kanjo-sen";
                townStart = new String[]{"Kita", "Minami", "Higashi", "Nishi", "Naka", "Shin", "Asa", "Taka", "Yama",
                        "Kawa", "Sakura", "Matsu", "Fuji", "Oka", "Hana", "Tsuru", "Ichi", "Mizu", "Ishi", "Kami"};
                townEnd = new String[]{"mura", "machi", "gawa", "yama", "hara", "saki", "shima", "bashi", "zawa",
                        "oka", "no", "ta", "kawa", "da"};
                districtWords = new String[]{"Sakura", "Chuo", "Kita", "Minami", "Higashi", "Nishi", "Shin", "Hon",
                        "Aoba", "Midori", "Asahi", "Kasuga", "Sakae", "Fuji", "Tenjin", "Ekimae", "Oka", "Matsu", "Ume", "Hikari"};
                hamletEnds = new String[]{" Village", " Farm", "-mura", " Fields", "-sato", " Hollow", " Bridge", " Shrine"};
                churches = new String[]{"Hachiman Shrine", "Kannon Temple", "Inari Shrine", "Jizo Temple",
                        "Tenmangu Shrine", "Kotoku Temple", "Hie Shrine", "Zenko Temple"};
                schools = new String[]{"Chuo High School", "Sakura Elementary", "Kita Junior High", "Minami High School",
                        "Aoba Elementary", "Higashi Junior High"};
                markets = new String[]{"Lawsun", "FamilyMarket", "AEONE", "Seiyo", "Maruetsu", "Gyomu Super"};
                pharmacies = new String[]{"Matsumoto Drug", "Sugi Pharmacy", "Welcia", "Tsuruha Drug"};
                gunStores = new String[]{"Yamato Hunting Supply", "Hokuto Outdoor", "Kumagai Sporting Guns",
                        "Shinano Hunters"};
                bases = new String[]{"Camp Asaka", "Camp Nerima", "Camp Itami", "Camp Kita-Kumamoto", "Camp Sendai",
                        "Camp Fuji"};
                hospital = "Municipal Hospital";
                policeStation = "Police Station ";
                fireStation = "Fire Station ";
                first = new String[]{"Haruto", "Yui", "Sota", "Hina", "Yuto", "Mio", "Riku", "Sakura", "Ren", "Aoi",
                        "Kaito", "Yuna", "Hiroshi", "Keiko", "Takashi", "Yuki", "Kenji", "Emi", "Daichi", "Rin", "Sho",
                        "Hana", "Takumi", "Nanami", "Akira", "Mei", "Kazuki", "Ayaka", "Ryo", "Saki"};
                last = new String[]{"Sato", "Suzuki", "Takahashi", "Tanaka", "Watanabe", "Ito", "Yamamoto", "Nakamura",
                        "Kobayashi", "Kato", "Yoshida", "Yamada", "Sasaki", "Yamaguchi", "Matsumoto", "Inoue", "Kimura",
                        "Hayashi", "Shimizu", "Mori", "Ikeda", "Hashimoto", "Abe", "Ishikawa", "Ogawa", "Fujita"};
                dogs = new String[]{"Hachi", "Kuro", "Shiro", "Momo", "Coco", "Sora", "Maru", "Hana", "Kotetsu",
                        "Pochi", "Chibi", "Kuu", "Mugi", "Azuki", "Taro", "Sakura"};
                carModels = new int[]{3, 3, 3, 3, 4, 4, 0, 5, 5, 6, 7};
                carColors = new int[]{0xFFF2F2F2, 0xFFF2F2F2, 0xFFF2F2F2, 0xFFB8BCC0, 0xFFB8BCC0, 0xFF1A1A1C,
                        0xFFE8D8B0, 0xFF8EC0D8, 0xFFD87A8A};
                taxiColor = 0xFF2E7A4A;
                busColor = 0xFFE8EAE4;
                pavement = 0xFFA6A49E;
                grass = 0xFF527A3E;
                officeRoofs = new int[]{0xFFB8BCC0, 0xFFA8ACB0, 0xFFC4C6C8, 0xFF9AA0A6, 0xFFB0B2AE, 0xFF8E949A};
                officeWalls = new int[]{0xFFE8E4DC, 0xFFD4D0C8, 0xFFC8C8C4, 0xFFDCD6C8, 0xFFB8BCC0};
                oldWalls = new int[]{0xFF6A4A32, 0xFF5A3E2A, 0xFFE8E2D2, 0xFF7A5A3E, 0xFFDCD2BC};
                oldRoofs = new int[]{0xFF2E3640, 0xFF3A4656, 0xFF26303A, 0xFF46505C};
                break;
            case FRANCE:
                leftHand = false;
                centreLine = 0xFFE6E6E2;
                houseRoofs = new int[]{0xFF7A8490, 0xFF6C7682, 0xFF4E5560, 0xFFB0623E, 0xFF9E5A3C, 0xFF5E6874};
                houseWalls = new int[]{0xFFE8DEC8, 0xFFDDD2B8, 0xFFEFE6D2, 0xFFD6C8A8, 0xFFE2D6BE, 0xFFC9BCA0};
                shopRoofs = new int[]{0xFF7A8490, 0xFF6C7682, 0xFF5E6874, 0xFF848E98, 0xFF707A86, 0xFF4E5560};
                shopWalls = new int[]{0xFFE8DEC8, 0xFF2A3A5A, 0xFF7A2A2A, 0xFF2E4A36, 0xFFEFE6D2, 0xFFD6C8A8};
                apartmentWalls = new int[]{0xFFE8DEC8, 0xFFDDD2B8, 0xFFEFE6D2, 0xFFD6C8A8, 0xFFE2D6BE};
                cruiserBody = 0xFFF2F2F2;
                cruiserDoor = 0xFF1F3A8A;
                cruiserRoof = 0;
                cruiserChecks = false;
                streets = new String[]{"Victor Hugo", "de la République", "Jean Jaurès", "des Lilas", "Pasteur",
                        "de la Paix", "Voltaire", "Gambetta", "du Marché", "de l'Église", "des Écoles", "Saint-Michel",
                        "de la Gare", "Molière", "des Roses", "Carnot", "Foch", "du Moulin", "de Verdun", "Émile Zola",
                        "des Tilleuls", "du Château", "de la Liberté", "Général de Gaulle", "Jules Ferry", "des Acacias",
                        "Saint-Jacques", "de la Mairie", "du Pont", "des Peupliers", "Clemenceau", "de Strasbourg",
                        "de Lyon", "de Paris", "Montaigne", "Rousseau", "Diderot", "de la Fontaine", "du Port",
                        "des Vignes", "Sainte-Anne", "de la Poste", "Lamartine", "Racine", "des Écuries", "du Stade",
                        "des Halles", "Danton", "Mozart", "Berlioz"};
                mainWords = new String[]{"Avenue", "Boulevard", "Avenue", "Cours"};
                localWords = new String[]{"Rue", "Rue", "Rue", "Allée", "Impasse", "Place", "Chemin", "Passage"};
                typeFirst = true;
                ringRoad = "Périphérique";
                townStart = new String[]{"Beau", "Mont", "Ville", "Château", "Fontaine", "Roche", "Pont", "Bourg",
                        "Val", "Clair", "Champ", "Vieux", "Belle", "Saint-Amand-", "Chaumont-", "Neuf"};
                townEnd = new String[]{"mont", "ville", "fort", "lieu", "bourg", "champ", "val", "mare", "court", "ry",
                        "-sur-Loire", "-les-Bains", "-la-Forêt", "-sur-Mer"};
                districtWords = new String[]{"Saint-Michel", "Le Marais", "Montmartre", "Les Halles", "Bellevue",
                        "Les Lilas", "La Croix", "Le Moulin", "Beaulieu", "Les Chênes", "Le Port", "La Gare",
                        "Les Vignes", "Le Château", "Sainte-Anne", "Les Tilleuls", "Le Parc", "La Plaine", "Les Prés", "Le Bourg"};
                hamletEnds = new String[]{"-le-Petit", " Village", "-les-Champs", " Ferme", "-la-Croix", "-le-Haut",
                        "-le-Bas", " Hameau"};
                churches = new String[]{"Église Saint-Pierre", "Notre-Dame", "Église Sainte-Anne", "Église Saint-Martin",
                        "Saint-Étienne", "Église Saint-Jean", "Sacré-Cœur", "Église Saint-Louis"};
                schools = new String[]{"Lycée Victor Hugo", "École Jules Ferry", "Collège Pasteur", "Lycée Voltaire",
                        "École des Lilas", "Collège Jean Moulin"};
                markets = new String[]{"Carrefive", "Monoprix", "Intermarché", "SuperU", "Casino", "Franprix"};
                pharmacies = new String[]{"Pharmacie Centrale", "Pharmacie de la Gare", "Pharmacie du Marché",
                        "Grande Pharmacie"};
                gunStores = new String[]{"Armurerie Lefèvre", "Chasse & Pêche", "Armurerie du Centre", "Armes Duval"};
                bases = new String[]{"Quartier Lyautey", "Camp de Mailly", "Caserne Bessières", "Quartier Foch",
                        "Camp de Canjuers", "Caserne Vauban"};
                hospital = "Hôpital Central";
                policeStation = "Commissariat ";
                fireStation = "Caserne de Pompiers ";
                first = new String[]{"Gabriel", "Jade", "Louis", "Louise", "Raphaël", "Emma", "Jules", "Alice", "Adam",
                        "Chloé", "Lucas", "Léa", "Hugo", "Manon", "Arthur", "Inès", "Nathan", "Camille", "Théo", "Sarah",
                        "Pierre", "Marie", "Antoine", "Claire", "Mehdi", "Yasmine", "Julien", "Élodie", "Paul", "Margaux"};
                last = new String[]{"Martin", "Bernard", "Dubois", "Thomas", "Robert", "Richard", "Petit", "Durand",
                        "Leroy", "Moreau", "Simon", "Laurent", "Lefebvre", "Michel", "Garcia", "David", "Bertrand",
                        "Roux", "Vincent", "Fournier", "Morel", "Girard", "Bonnet", "Dupont", "Lambert", "Benali"};
                dogs = new String[]{"Rex", "Filou", "Médor", "Pistache", "Caramel", "Oscar", "Lilou", "Nala", "Ulysse",
                        "Praline", "Biscotte", "Hercule", "Gaston", "Pomme", "Vanille", "Tintin"};
                carModels = new int[]{4, 4, 4, 4, 4, 0, 5, 5, 2, 6, 7};
                carColors = new int[]{0xFF8A8F96, 0xFFE8E8E8, 0xFF2A2C30, 0xFF2E4F8A, 0xFFB03A2E, 0xFF5A5E66,
                        0xFFE8E0D0, 0xFF3C6A4E};
                taxiColor = 0xFF1A1A1C;
                busColor = 0xFFE8E8EC;
                pavement = 0xFFAEA592;
                grass = 0xFF56723A;
                plaza = 0xFFC2B494;
                officeRoofs = new int[]{0xFF7A8490, 0xFF6C7682, 0xFF848E98, 0xFF5E6874, 0xFF707A86, 0xFF8A9298};
                officeWalls = new int[]{0xFFE8DEC8, 0xFFDDD2B8, 0xFFEFE6D2, 0xFFD6C8A8, 0xFFE2D6BE};
                oldWalls = new int[]{0xFFE8DEC8, 0xFFDDD2B8, 0xFFD6C8A8, 0xFFCCBC98, 0xFFEFE6D2};
                oldRoofs = new int[]{0xFF7A8490, 0xFF5E6874, 0xFFB0623E, 0xFF4E5560};
                break;
            case MEXICO:
                leftHand = false;
                centreLine = 0xFFE0B83A;
                houseRoofs = new int[]{0xFFC9BFAE, 0xFFB4583A, 0xFFA04A30, 0xFFD2C6B2, 0xFFBE6A44, 0xFFB8AE9C};
                houseWalls = new int[]{0xFFE07A8C, 0xFFF0C04A, 0xFF4FB8B0, 0xFFE8873A, 0xFFF2EEE4, 0xFF7A9AD8};
                shopRoofs = new int[]{0xFFC9BFAE, 0xFFD2C6B2, 0xFFB8AE9C, 0xFFB4583A, 0xFFC4B8A4, 0xFFBE6A44};
                shopWalls = new int[]{0xFFE8504A, 0xFF3FA860, 0xFFF0C04A, 0xFF4FB8B0, 0xFFE07A8C, 0xFF7A5AC0};
                apartmentWalls = new int[]{0xFFF0C04A, 0xFFE8873A, 0xFFE2DAC8, 0xFF4FB8B0, 0xFFE07A8C};
                cruiserBody = 0xFFF2F2F2;
                cruiserDoor = 0xFF1F5FB8;
                cruiserRoof = 0;
                cruiserChecks = false;
                streets = new String[]{"Juárez", "Hidalgo", "Morelos", "Reforma", "Insurgentes", "5 de Mayo", "Madero",
                        "Zaragoza", "Allende", "Guerrero", "de las Flores", "Independencia", "Revolución", "Obregón",
                        "Constitución", "Benito Juárez", "Zapata", "Aldama", "Matamoros", "Victoria", "Galeana",
                        "Bravo", "Abasolo", "Iturbide", "16 de Septiembre", "Juan Escutia", "Niños Héroes", "Colón",
                        "Cuauhtémoc", "Moctezuma", "Las Palmas", "Los Pinos", "del Sol", "de la Luna", "Jacarandas",
                        "Bugambilias", "Tulipanes", "Nogales", "Fresno", "Álamo", "Mercado", "de la Iglesia",
                        "Ferrocarril", "del Río", "Lázaro Cárdenas", "Pino Suárez", "Carranza", "Vasconcelos",
                        "Sor Juana", "Frida Kahlo"};
                mainWords = new String[]{"Avenida", "Calzada", "Boulevard", "Avenida"};
                localWords = new String[]{"Calle", "Calle", "Calle", "Privada", "Callejón", "Andador", "Cerrada"};
                typeFirst = true;
                ringRoad = "Periférico";
                townStart = new String[]{"San Juan", "Santa María", "San Miguel", "Villa", "Puerto", "Ciudad",
                        "Santa Cruz", "San Pedro", "Valle", "Real", "San Andrés", "Santa Rosa"};
                townEnd = new String[]{" del Río", " de Allende", " de las Flores", " Nuevo", " de Hidalgo", " del Valle",
                        " de Juárez", " Viejo", " del Monte", " de Guadalupe"};
                districtWords = new String[]{"Las Palmas", "El Centro", "San Ángel", "La Condesa", "Los Pinos",
                        "Santa Fe", "El Carmen", "La Merced", "San Rafael", "Las Flores", "Jardines", "Lomas", "El Mirador",
                        "La Loma", "San José", "El Barrio", "Del Valle", "Los Álamos", "Guadalupe", "Tlalpan"};
                hamletEnds = new String[]{" Rancho", " Ejido", " Hacienda", " Pueblo", " Ranchería", " La Cruz",
                        " El Puente", " Los Pozos"};
                churches = new String[]{"Parroquia de San José", "Templo de Guadalupe", "Iglesia de Santo Domingo",
                        "Catedral de la Asunción", "Templo de San Francisco", "Parroquia de San Miguel",
                        "Iglesia del Carmen", "Templo de Santa Rosa"};
                schools = new String[]{"Escuela Benito Juárez", "Secundaria Técnica 12", "Preparatoria Hidalgo",
                        "Primaria Niños Héroes", "Colegio Morelos", "Escuela Sor Juana"};
                markets = new String[]{"Soriano", "Chedrahui", "Bodega Aurrera", "La Comer", "Mercado Municipal", "OXXO Grande"};
                pharmacies = new String[]{"Farmacia Guadalajara", "Farmacias del Ahorro", "Farmacia San Pablo",
                        "Farmacia Similar"};
                gunStores = new String[]{"Armería del Norte", "Club de Caza Sierra", "Deportes Cinegéticos",
                        "Armería Federal"};
                bases = new String[]{"Campo Militar 1", "Zona Militar Norte", "Cuartel Hidalgo", "Campo Militar Morelos",
                        "Base Aérea Santa Lucía", "Cuartel General Zaragoza"};
                hospital = "Hospital General";
                policeStation = "Comandancia ";
                fireStation = "Estación de Bomberos ";
                first = new String[]{"Santiago", "Sofía", "Mateo", "Valentina", "Sebastián", "Regina", "Leonardo",
                        "Camila", "Diego", "Ximena", "Emiliano", "María José", "José", "Guadalupe", "Juan", "Fernanda",
                        "Miguel", "Daniela", "Carlos", "Andrea", "Luis", "Lucía", "Alejandro", "Paola", "Jorge", "Itzel",
                        "Fernando", "Renata", "Rafael", "Mariana"};
                last = new String[]{"Hernández", "García", "Martínez", "López", "González", "Rodríguez", "Pérez",
                        "Sánchez", "Ramírez", "Cruz", "Flores", "Gómez", "Morales", "Vázquez", "Reyes", "Jiménez",
                        "Torres", "Díaz", "Gutiérrez", "Ruiz", "Mendoza", "Aguilar", "Ortiz", "Castillo", "Romero", "Chávez"};
                dogs = new String[]{"Firulais", "Canela", "Chispa", "Max", "Luna", "Toby", "Manchas", "Princesa",
                        "Pelusa", "Chato", "Negro", "Lobo", "Coco", "Rocky", "Chiquita", "Bruno"};
                carModels = new int[]{8, 8, 0, 0, 1, 1, 1, 4, 2, 6, 6, 7};
                carColors = new int[]{0xFFF2F2F2, 0xFFB03A2E, 0xFF2E5FB0, 0xFF3C8A4E, 0xFFE0C04A, 0xFF8A8F96,
                        0xFFE07A2E, 0xFF5AB8C8};
                taxiColor = 0xFFE0708C;
                busColor = 0xFF3FA05A;
                pavement = 0xFFB49C80;
                grass = 0xFF77803F;
                plaza = 0xFFC8A880;
                officeRoofs = new int[]{0xFFCFC4B0, 0xFFC0B49C, 0xFFD8CEBA, 0xFFB8AC96, 0xFFC8BCA6, 0xFFB4583A};
                officeWalls = new int[]{0xFFF2EEE4, 0xFFF0C04A, 0xFFE8873A, 0xFF4FB8B0, 0xFFE07A8C, 0xFFE2DAC8};
                oldWalls = new int[]{0xFFE07A8C, 0xFFF0C04A, 0xFF4FB8B0, 0xFFE8873A, 0xFF7A9AD8, 0xFFC8503A};
                oldRoofs = new int[]{0xFFB4583A, 0xFFA04A30, 0xFFC9BFAE, 0xFFBE6A44};
                break;
            case SWITZERLAND:
                leftHand = false;
                centreLine = 0xFFE8E8E4;
                houseRoofs = new int[]{0xFF6A4A3A, 0xFF8A4A32, 0xFF4E5560, 0xFF7A5A48, 0xFF5A3D35, 0xFF9A5B3C};
                houseWalls = new int[]{0xFFF2EEE4, 0xFFE8DEC8, 0xFFB88A5A, 0xFFDCD6C8, 0xFFE0D6C4, 0xFFC9A878};
                shopRoofs = new int[]{0xFF6A4A3A, 0xFF8A9096, 0xFF4E5560, 0xFF7A5A48, 0xFF6C747C, 0xFF5A6670};
                shopWalls = new int[]{0xFFF2EEE4, 0xFFD02A2A, 0xFFE8DEC8, 0xFF2E5A3A, 0xFFE0D0A8, 0xFFDCD6C8};
                apartmentWalls = new int[]{0xFFF2EEE4, 0xFFE8DEC8, 0xFFDCD6C8, 0xFFE0D0A8, 0xFFC8C4BC};
                cruiserBody = 0xFFF2F2F2;
                cruiserDoor = 0xFFE87A1A;
                cruiserRoof = 0;
                cruiserChecks = true;
                checkA = 0xFFE87A1A;
                checkB = 0xFF1F3A8A;
                streets = new String[]{"Bahnhof", "Haupt", "Kirch", "Dorf", "See", "Berg", "Markt", "Schul", "Post",
                        "Linden", "Rosen", "Garten", "Bären", "Löwen", "Sonnen", "Wald", "Brunnen", "Mühle", "Rhein",
                        "Aare", "Limmat", "Zürcher", "Berner", "Basler", "Luzerner", "Tell", "Alpen", "Matten", "Rain",
                        "Tannen", "Eichen", "Buchen", "Felsen", "Allmend", "Schützen", "Zeughaus", "Spital", "Rathaus",
                        "Ober", "Unter", "Neu", "Alt", "Hof", "Kloster", "Brücken", "Wiesen", "Hügel", "Sennhof",
                        "Gotthard", "Pilatus"};
                mainWords = new String[]{"strasse", "strasse", "allee", " Ring"};
                localWords = new String[]{"gasse", "weg", "strasse", "gasse", "platz", "rain"};
                typeFirst = false;
                ringRoad = "Ring";
                townStart = new String[]{"Ober", "Unter", "Neu", "Alt", "Kirch", "Berg", "See", "Wald", "Burg",
                        "Rhein", "Stein", "Rapp", "Wil", "Lang", "Hinter", "Vorder", "Grind", "Engel"};
                townEnd = new String[]{"dorf", "berg", "wil", "ikon", "ingen", "au", "egg", "matt", "bach", "see",
                        "stein", "wald", "hausen", "brunnen"};
                districtWords = new String[]{"Seefeld", "Enge", "Wiedikon", "Oberdorf", "Unterstadt", "Matte",
                        "Breitenrain", "Kirchenfeld", "Gundeli", "Klein", "Neubad", "Sonnenberg", "Waldegg",
                        "Rosenberg", "Bühl", "Hirslanden", "Altstetten", "Wollishofen", "Hottingen", "Lindenhof"};
                hamletEnds = new String[]{"alp", "hof", "matt", "egg", " Weiler", "boden", "weid", "rüti"};
                churches = new String[]{"Grossmünster", "Fraumünster", "St. Peter", "Berner Münster", "Kirche St. Martin",
                        "Reformierte Kirche", "Kirche St. Anna", "Kapelle St. Jakob"};
                schools = new String[]{"Kantonsschule", "Primarschule Dorf", "Sekundarschule See", "Gymnasium Rämibühl",
                        "Schulhaus Matte", "Berufsschule"};
                markets = new String[]{"Migrol", "Coup", "Denner", "Volg", "Aldi Suisse", "Manor Food"};
                pharmacies = new String[]{"Apotheke am Bahnhof", "Amavita Apotheke", "Sun Store", "Dorf-Apotheke"};
                gunStores = new String[]{"Waffen Hofer", "Schützenbedarf Kern", "Jagd & Sport Brunner", "Waffenladen Tell"};
                bases = new String[]{"Kaserne Thun", "Kaserne Bière", "Waffenplatz Kloten", "Kaserne Aarau",
                        "Waffenplatz Isone", "Kaserne Bern"};
                hospital = "Kantonsspital";
                policeStation = "Polizeiposten ";
                fireStation = "Feuerwehr ";
                first = new String[]{"Noah", "Mia", "Luca", "Emma", "Leon", "Lea", "Matteo", "Sofia", "Elias", "Lina",
                        "Liam", "Laura", "Nico", "Anna", "Jonas", "Elena", "Samuel", "Chiara", "David", "Nina", "Urs",
                        "Heidi", "Beat", "Ursula", "Reto", "Vreni", "Jan", "Sara", "Fabian", "Alessia"};
                last = new String[]{"Müller", "Meier", "Schmid", "Keller", "Weber", "Huber", "Schneider", "Meyer",
                        "Steiner", "Fischer", "Gerber", "Brunner", "Baumann", "Frei", "Zimmermann", "Moser", "Widmer",
                        "Wyss", "Graf", "Roth", "Rossi", "Bernasconi", "Favre", "Bonvin", "Kälin", "Suter"};
                dogs = new String[]{"Bello", "Barry", "Luna", "Rex", "Sämi", "Kira", "Bobby", "Gina", "Nero", "Fido",
                        "Bläss", "Lumpi", "Tasso", "Mira", "Rocky", "Chico"};
                carModels = new int[]{4, 4, 4, 0, 0, 2, 2, 5, 6, 7};
                carColors = new int[]{0xFF2A2C30, 0xFFE8E8E8, 0xFF8A8F96, 0xFF2E4F8A, 0xFFB03A2E, 0xFF5A5E66, 0xFF3C6A4E};
                taxiColor = 0xFFE8E8E8;
                busColor = 0xFFE8C21A;
                pavement = 0xFFA8A6A0;
                grass = 0xFF4E8A3A;
                officeRoofs = new int[]{0xFF8A9096, 0xFF6C747C, 0xFF9AA2AA, 0xFF5A6670, 0xFF6A4A3A};
                officeWalls = new int[]{0xFFF2EEE4, 0xFFE8DEC8, 0xFFDCD6C8, 0xFFC8C8C4, 0xFFB8BCC0};
                oldWalls = new int[]{0xFFF2EEE4, 0xFFE8DEC8, 0xFFD0B888, 0xFFB88A5A, 0xFFE0D0A8};
                oldRoofs = new int[]{0xFF6A4A3A, 0xFF8A4A32, 0xFF5A3D35, 0xFF4E5560};
                look = FRANCE;
                churchLook = FRANCE;
                shieldColor = 0xFFC02020;
                break;
            case KOREA:
                leftHand = false;
                centreLine = 0xFFE0B83A;
                houseRoofs = new int[]{0xFF2E6A8A, 0xFF3F7A5A, 0xFF8A3A2E, 0xFF3A4656, 0xFF4A5A70, 0xFF5A6670};
                houseWalls = new int[]{0xFFEDE8DC, 0xFFDCD6C8, 0xFFB8A48A, 0xFFE8E2D2, 0xFFC9C2B4, 0xFFF2EEE6};
                shopRoofs = new int[]{0xFF8A9096, 0xFF6C747C, 0xFF9AA2AA, 0xFF5A6670, 0xFF2E6A8A, 0xFF3F7A5A};
                shopWalls = new int[]{0xFFF2F0EA, 0xFFE0DCD4, 0xFFD02A2A, 0xFF2A5AA8, 0xFFF0D040, 0xFF3FA860};
                apartmentWalls = new int[]{0xFFF2F0EA, 0xFFE8E4DC, 0xFFDCD8D0, 0xFFE8E0C8, 0xFFD4D8DC};
                cruiserBody = 0xFFF2F2F2;
                cruiserDoor = 0xFF1F4FA8;
                cruiserRoof = 0;
                cruiserChecks = false;
                streets = new String[]{"Sejong", "Teheran", "Jongno", "Eulji", "Toegye", "Dosan", "Apgujeong",
                        "Gangnam", "Hangang", "Namdaemun", "Yulgok", "Samil", "Insadong", "Sinchon", "Hongik", "Yeouido",
                        "Mapo", "Itaewon", "Myeong", "Bukchon", "Gwanghwamun", "Dongho", "Seolleung", "Bongeunsa",
                        "Banpo", "Jamsil", "Olympic", "Hyoja", "Changgyeong", "Daehak", "Haean", "Jungang", "Saemal",
                        "Mugunghwa", "Sopung", "Gukhwa", "Baekbeom", "Ttukseom", "Ewha", "Sindang", "Gongdeok",
                        "Hwarang", "Cheonho", "Seongsu", "Wangsimni", "Nonhyeon", "Yeoksam", "Dongil", "Haneul", "Bom"};
                mainWords = new String[]{"-daero", "-ro", "-daero", "-ro"};
                localWords = new String[]{"-gil", "-gil", "-ro", "-gil", "-ro", "-gil"};
                typeFirst = false;
                ringRoad = "Sunhwan-ro";
                townStart = new String[]{"Cheong", "Gwang", "Dae", "Jeon", "Su", "Seong", "An", "Gim", "Yang",
                        "Pyeong", "Hwa", "Mok", "Chun", "Won", "Gyeong", "Pa", "Il", "Song"};
                townEnd = new String[]{"ju", "won", "san", "cheon", "dong", "pyeong", "seong", "po", "gang", "yang",
                        "nam", "buk", "ri", "do"};
                districtWords = new String[]{"Gangnam", "Jongno", "Mapo", "Seocho", "Songpa", "Yeongdeungpo",
                        "Seongdong", "Dongdaemun", "Gwangjin", "Nowon", "Eunpyeong", "Gangseo", "Guro", "Yangcheon",
                        "Jung", "Seodaemun", "Dobong", "Bukchon", "Haeundae", "Suyeong"};
                hamletEnds = new String[]{"-ri", " Maeul", "-dong", " Farm", "-gol", " Village", "-teo", " Orchard"};
                churches = new String[]{"Yoido Full Gospel Church", "Myeongdong Cathedral", "Jogyesa Temple",
                        "Bongeunsa Temple", "Somang Church", "Onnuri Church", "Yakhyeon Cathedral", "Hwagyesa Temple"};
                schools = new String[]{"Hangang High School", "Sejong Elementary", "Gangnam Middle School",
                        "Daehan High School", "Mugunghwa Elementary", "Seoul Science High"};
                markets = new String[]{"E-Mark", "HomePlus", "Lotty Mart", "GS26", "CU Mart", "Gwangjang Market"};
                pharmacies = new String[]{"Olive Young", "Onnuri Pharmacy", "Daehan Yakguk", "Seoul Pharmacy"};
                gunStores = new String[]{"Hanguk Hunting", "Daehan Shooting Supply", "Baekdu Outdoor", "Taeguk Sporting"};
                bases = new String[]{"Camp Gyeryong", "Camp Pocheon", "Yongsan Garrison", "Camp Wonju", "Camp Cheorwon",
                        "Camp Paju"};
                hospital = "University Hospital";
                policeStation = "Police Station ";
                fireStation = "Fire Station ";
                first = new String[]{"Min-jun", "Seo-yeon", "Ji-ho", "Ha-eun", "Do-yun", "Ji-woo", "Seo-jun", "Su-ah",
                        "Ye-jun", "Ji-yoo", "Hyun-woo", "Min-seo", "Jae-won", "Da-eun", "Sung-min", "Eun-ji", "Tae-yang",
                        "Hye-jin", "Dong-hyun", "So-yeon", "Jin", "Yuna", "Joon", "Mina", "Woo-jin", "Na-rae",
                        "Kyung-soo", "Bo-ra", "Sang-hoon", "Ji-min"};
                last = new String[]{"Kim", "Lee", "Park", "Choi", "Jung", "Kang", "Cho", "Yoon", "Jang", "Lim",
                        "Han", "Oh", "Seo", "Shin", "Kwon", "Hwang", "Ahn", "Song", "Jeon", "Hong", "Yoo", "Ko", "Moon",
                        "Yang", "Son", "Bae"};
                dogs = new String[]{"Baduk", "Dubu", "Mandu", "Bori", "Kongi", "Choco", "Haru", "Dari", "Nuri",
                        "Ttangkong", "Sarang", "Jindo", "Kkami", "Maru", "Bomi", "Hodu"};
                carModels = new int[]{0, 0, 0, 2, 2, 2, 4, 3, 5, 6, 7};
                carColors = new int[]{0xFFF2F2F2, 0xFFF2F2F2, 0xFF1A1A1C, 0xFFB8BCC0, 0xFF8A8F96, 0xFF2A2C30, 0xFF2E4F8A};
                taxiColor = 0xFFE07A2E;
                busColor = 0xFF2E7AC0;
                pavement = 0xFFA8A4A0;
                grass = 0xFF5A7A3E;
                officeRoofs = new int[]{0xFFB8BCC0, 0xFFA8ACB0, 0xFFC4C6C8, 0xFF9AA0A6, 0xFF8E949A};
                officeWalls = new int[]{0xFFE8E4DC, 0xFFD4D0C8, 0xFFC8C8C4, 0xFF9CB4C8, 0xFFB8BCC0};
                oldWalls = new int[]{0xFFE8E2D2, 0xFFDCD2BC, 0xFFB8A48A, 0xFF8A3A2E, 0xFFC9C2B4};
                oldRoofs = new int[]{0xFF3A4656, 0xFF2E3640, 0xFF2E6A8A, 0xFF46505C};
                look = JAPAN;
                shieldColor = 0xFF1E5AA8;
                break;
            case MALAYSIA:
                leftHand = true;
                centreLine = 0xFFE8E8E4;
                houseRoofs = new int[]{0xFF8A9096, 0xFFB0583A, 0xFF7A2E28, 0xFF4C5048, 0xFF9A3A2E, 0xFF5A7A8A};
                houseWalls = new int[]{0xFFF2EEE4, 0xFFE8D8B0, 0xFF9AC8B0, 0xFFE0C090, 0xFFB8D0E0, 0xFFE8B8A0};
                shopRoofs = new int[]{0xFFB0583A, 0xFF8A9096, 0xFF7A2E28, 0xFF9A3A2E, 0xFF6C747C, 0xFF4C5048};
                shopWalls = new int[]{0xFFE8D8B0, 0xFF9AC8B0, 0xFFE0C090, 0xFFB8D0E0, 0xFFE8B8A0, 0xFFF2EEE4};
                apartmentWalls = new int[]{0xFFF2EEE4, 0xFFE8E0C8, 0xFFD4D8DC, 0xFFE0C090, 0xFFB8D0E0};
                cruiserBody = 0xFFF2F2F2;
                cruiserDoor = 0xFF1F3F8A;
                cruiserRoof = 0;
                cruiserChecks = true;
                checkA = 0xFF1F3F8A;
                checkB = 0xFFE03A3A;
                streets = new String[]{"Tun Razak", "Ampang", "Bukit Bintang", "Sultan Ismail", "Raja Chulan",
                        "Tuanku Abdul Rahman", "Masjid India", "Petaling", "Pudu", "Imbi", "Cheras", "Klang Lama",
                        "Bangsar", "Damansara", "Kuching", "Ipoh", "Melaka", "Pahang", "Kelantan", "Perak", "Kenanga",
                        "Melati", "Cempaka", "Mawar", "Seroja", "Kemboja", "Bunga Raya", "Merdeka", "Sentosa", "Bahagia",
                        "Hang Tuah", "Hang Jebat", "Tunku", "Dato Onn", "Ahmad Shah", "Pantai", "Bukit", "Sungai", "Kebun",
                        "Pasar", "Stesen", "Sekolah", "Hospital", "Besar", "Baru", "Lama", "Kampung", "Tasik", "Gurney",
                        "Penang"};
                mainWords = new String[]{"Jalan", "Lebuhraya", "Jalan", "Persiaran"};
                localWords = new String[]{"Jalan", "Jalan", "Lorong", "Lorong", "Lebuh", "Persiaran"};
                typeFirst = true;
                ringRoad = "Jalan Lingkaran";
                townStart = new String[]{"Kuala ", "Bukit ", "Sungai ", "Kampung ", "Port ", "Tanjung ", "Batu ",
                        "Teluk ", "Pulau ", "Kota ", "Bandar ", "Seri "};
                townEnd = new String[]{"Lumpur", "Mertajam", "Petani", "Pahang", "Raja", "Indah", "Jaya", "Baru",
                        "Selangor", "Damai", "Permai", "Bahru", "Kemuning", "Bintang"};
                districtWords = new String[]{"Melati", "Kenanga", "Cempaka", "Mawar", "Seroja", "Sri Hartamas",
                        "Desa", "Bangsar", "Damansara", "Cheras", "Ampang", "Setapak", "Wangsa Maju", "Kepong",
                        "Puchong", "Subang", "Bukit Jalil", "Sentul", "Pudu", "Kemuning"};
                hamletEnds = new String[]{" Kampung", " Ladang", " Estate", " Hulu", " Hilir", " Tengah", " Felda", " Paya"};
                churches = new String[]{"Masjid Jamek", "Masjid Negara", "Sri Mahamariamman Temple", "St. Mary's Cathedral",
                        "Thean Hou Temple", "Masjid Al-Hidayah", "Sin Sze Si Ya Temple", "Masjid Putra"};
                schools = new String[]{"SMK Bukit Bintang", "SK Taman Melati", "SMK Damansara Jaya", "SJK(C) Kuen Cheng",
                        "SK Sultan Ismail", "Kolej Tunku Kurshiah"};
                markets = new String[]{"Giant", "Mydin", "Jaya Grocer", "99 Speedmark", "Econsave", "Pasar Besar"};
                pharmacies = new String[]{"Guardian", "Watsons", "Caring Pharmacy", "Farmasi Alpro"};
                gunStores = new String[]{"Kedai Senjata Hang Tuah", "Malaysia Hunting Supply", "Sporting Arms KL", "Rimba Outdoor"};
                bases = new String[]{"Kem Sungai Besi", "Kem Terendak", "Kem Batu Kentonment", "Kem Paya Jaras",
                        "Kem Mahkota", "Kem Syed Sirajuddin"};
                hospital = "Hospital Besar";
                policeStation = "Balai Polis ";
                fireStation = "Balai Bomba ";
                first = new String[]{"Muhammad", "Nur", "Ahmad", "Siti", "Aiman", "Aisyah", "Hafiz", "Nurul", "Amir",
                        "Farah", "Wei Jie", "Mei Ling", "Jun Hao", "Xin Yi", "Kumar", "Priya", "Arjun", "Kavitha", "Zul",
                        "Izzah", "Daniel", "Sarah", "Haziq", "Alya", "Raj", "Lakshmi", "Ken", "Jia Hui", "Faiz", "Hana"};
                last = new String[]{"bin Abdullah", "binti Ahmad", "bin Ismail", "binti Hassan", "bin Ibrahim", "Tan",
                        "Lim", "Lee", "Wong", "Ng", "Chong", "Raj", "Kumar", "Subramaniam", "Pillai", "bin Yusof",
                        "binti Osman", "Goh", "Chin", "Ong", "Teoh", "Nair", "bin Razak", "binti Omar", "Yap", "Loh"};
                dogs = new String[]{"Lucky", "Coco", "Milo", "Snowy", "Lucky", "Ah Huat", "Bobo", "Tiger", "Kopi",
                        "Kaya", "Mochi", "Rambo", "Bubu", "Lulu", "Max", "Boy"};
                carModels = new int[]{4, 4, 4, 0, 0, 3, 2, 1, 5, 6, 7};
                carColors = new int[]{0xFFF2F2F2, 0xFF8A8F96, 0xFF2A2C30, 0xFFB03A2E, 0xFF2E5FB0, 0xFFE8E0D0, 0xFF8A3A5A};
                taxiColor = 0xFFB03A2E;
                busColor = 0xFFE03A3A;
                pavement = 0xFFA8A090;
                grass = 0xFF4E8A36;
                officeRoofs = new int[]{0xFFB8BCC0, 0xFFA8ACB0, 0xFF9AA0A6, 0xFF8E949A, 0xFFC4C6C8};
                officeWalls = new int[]{0xFFE8E4DC, 0xFF9CB4C8, 0xFFC8C8C4, 0xFFD4D0C8, 0xFFB8BCC0};
                oldWalls = new int[]{0xFFE8D8B0, 0xFF9AC8B0, 0xFFE0C090, 0xFFB8D0E0, 0xFFE8B8A0};
                oldRoofs = new int[]{0xFFB0583A, 0xFF7A2E28, 0xFF9A3A2E, 0xFF8A9096};
                look = AUSTRALIA;
                churchLook = MEXICO;
                shieldColor = 0xFF1E6A3A;
                break;
            case PORTUGAL:
                leftHand = false;
                centreLine = 0xFFE8E8E4;
                houseRoofs = new int[]{0xFFB4583A, 0xFFA04A30, 0xFFBE6A44, 0xFFC0703E, 0xFF9E5A3C, 0xFFB86040};
                houseWalls = new int[]{0xFFF2EEE4, 0xFFF2E6C0, 0xFFE8C8A0, 0xFFB8D0E8, 0xFFF0D8D0, 0xFFE8E0D0};
                shopRoofs = new int[]{0xFFB4583A, 0xFFA04A30, 0xFFBE6A44, 0xFF8A9096, 0xFF9E5A3C, 0xFF6C747C};
                shopWalls = new int[]{0xFFF2EEE4, 0xFF2E5AA8, 0xFFF2E6C0, 0xFFB8D0E8, 0xFF2E6A4A, 0xFFE8C8A0};
                apartmentWalls = new int[]{0xFFF2EEE4, 0xFFF2E6C0, 0xFFB8D0E8, 0xFFF0D8D0, 0xFFE8E0D0};
                cruiserBody = 0xFF1E2E5A;
                cruiserDoor = 0xFF1E2E5A;
                cruiserRoof = 0xFFF2F2F2;
                cruiserChecks = true;
                checkA = 0xFF1E2E5A;
                checkB = 0xFFF2F2F2;
                streets = new String[]{"da Liberdade", "Augusta", "do Ouro", "da Prata", "de Santa Catarina", "Garrett",
                        "do Carmo", "da República", "dos Aliados", "de Camões", "de São Bento", "das Flores",
                        "Almirante Reis", "da Boavista", "de Belém", "do Comércio", "da Sé", "de São Jorge", "dos Restauradores",
                        "Marquês de Pombal", "de Ceuta", "Infante Dom Henrique", "Vasco da Gama", "de Sá da Bandeira",
                        "do Alecrim", "da Misericórdia", "das Janelas Verdes", "de Santo António", "do Mercado", "da Igreja",
                        "do Castelo", "da Estação", "das Laranjeiras", "dos Pescadores", "da Fonte", "do Moinho",
                        "dos Navegantes", "do Rossio", "de Alfama", "da Graça", "das Amoreiras", "do Sol", "da Lua",
                        "Fernando Pessoa", "Eça de Queirós", "dos Combatentes", "25 de Abril", "5 de Outubro",
                        "do Mar", "da Ribeira"};
                mainWords = new String[]{"Avenida", "Avenida", "Estrada", "Alameda"};
                localWords = new String[]{"Rua", "Rua", "Rua", "Travessa", "Beco", "Largo", "Calçada", "Escadinhas"};
                typeFirst = true;
                ringRoad = "Circular";
                townStart = new String[]{"Vila ", "Santa ", "São ", "Porto ", "Monte", "Castelo ", "Ponte de ",
                        "Torre", "Vale de ", "Alto ", "Póvoa de ", "Fonte "};
                townEnd = new String[]{"Real", "Nova", "Velha", "do Mar", "Verde", "Formosa", "Lima", "Branco",
                        "Maria", "do Castelo", "da Serra", "Alegre", "Grande", "do Conde"};
                districtWords = new String[]{"Alfama", "Graça", "Bairro Alto", "Chiado", "Mouraria", "Belém",
                        "Campo de Ourique", "Estrela", "Lapa", "Ribeira", "Foz", "Boavista", "Bonfim", "Campanhã",
                        "Alvalade", "Areeiro", "Benfica", "Marvila", "Ajuda", "Amoreiras"};
                hamletEnds = new String[]{" Aldeia", " Quinta", " Monte", " Casal", " Lugar", " Moinho", " Herdade", " Ponte"};
                churches = new String[]{"Sé Catedral", "Igreja de São Roque", "Mosteiro dos Jerónimos", "Igreja do Carmo",
                        "Igreja de Santo António", "Basílica da Estrela", "Igreja de São Vicente", "Capela das Almas"};
                schools = new String[]{"Escola Secundária Camões", "Escola Básica da Graça", "Liceu Pedro Nunes",
                        "Escola Básica do Bonfim", "Colégio Militar", "Escola Secundária D. Dinis"};
                markets = new String[]{"Pingo Ouro", "Continentes", "Minipreço", "Mercado da Ribeira", "Lidal", "Intermarché"};
                pharmacies = new String[]{"Farmácia Central", "Farmácia Estácio", "Farmácia da Baixa", "Farmácia do Rossio"};
                gunStores = new String[]{"Armeiro do Porto", "Casa de Caça Silva", "Armaria Lusitana", "Caça e Pesca Lima"};
                bases = new String[]{"Quartel da Ajuda", "Quartel de Santa Margarida", "Base de Tancos", "Quartel da Serra do Pilar",
                        "Regimento de Infantaria 1", "Quartel de Mafra"};
                hospital = "Hospital de Santa Maria";
                policeStation = "Esquadra da PSP ";
                fireStation = "Bombeiros Voluntários ";
                first = new String[]{"Francisco", "Maria", "Santiago", "Leonor", "Afonso", "Matilde", "Tomás", "Beatriz",
                        "Duarte", "Carolina", "João", "Inês", "Rodrigo", "Mariana", "Martim", "Ana", "Gonçalo", "Sofia",
                        "Diogo", "Rita", "Pedro", "Catarina", "Rui", "Joana", "Miguel", "Teresa", "Nuno", "Filipa",
                        "Luís", "Marta"};
                last = new String[]{"Silva", "Santos", "Ferreira", "Pereira", "Oliveira", "Costa", "Rodrigues",
                        "Martins", "Jesus", "Sousa", "Fernandes", "Gonçalves", "Gomes", "Lopes", "Marques", "Alves",
                        "Almeida", "Ribeiro", "Pinto", "Carvalho", "Teixeira", "Moreira", "Correia", "Mendes", "Nunes", "Soares"};
                dogs = new String[]{"Bobi", "Pantufa", "Tejo", "Nina", "Farrusco", "Pipoca", "Lua", "Tobias",
                        "Bolacha", "Kika", "Max", "Mel", "Nico", "Pastel", "Boneca", "Simba"};
                carModels = new int[]{4, 4, 4, 4, 0, 0, 5, 2, 6, 7};
                carColors = new int[]{0xFF8A8F96, 0xFFE8E8E8, 0xFF2A2C30, 0xFF2E4F8A, 0xFFB03A2E, 0xFF5A5E66, 0xFFE8E0D0};
                taxiColor = 0xFF2A2C30;
                busColor = 0xFFE8C21A;
                pavement = 0xFFD8D4CC;
                grass = 0xFF6E7A3A;
                plaza = 0xFFE4E0D8;
                officeRoofs = new int[]{0xFF8A9096, 0xFFB4583A, 0xFF9AA2AA, 0xFF6C747C, 0xFFA04A30};
                officeWalls = new int[]{0xFFF2EEE4, 0xFFF2E6C0, 0xFFE8C8A0, 0xFFB8D0E8, 0xFFDCD6C8};
                oldWalls = new int[]{0xFFF2E6C0, 0xFFB8D0E8, 0xFFF0D8D0, 0xFFE8C8A0, 0xFF6A9AC8};
                oldRoofs = new int[]{0xFFB4583A, 0xFFA04A30, 0xFFBE6A44, 0xFF9E5A3C};
                look = FRANCE;
                churchLook = FRANCE;
                shieldColor = 0xFF1E4AA8;
                break;
            case MOROCCO:
                leftHand = false;
                centreLine = 0xFFE8E8E4;
                houseRoofs = new int[]{0xFFD8CCB4, 0xFFC8B494, 0xFFE0D4BC, 0xFFB89C78, 0xFFD0C0A0, 0xFFC0A884};
                houseWalls = new int[]{0xFFF2EEE4, 0xFFE0A070, 0xFFD88A5A, 0xFFE8C890, 0xFF5A8AC0, 0xFFC86A4A};
                shopRoofs = new int[]{0xFFD8CCB4, 0xFFC8B494, 0xFFE0D4BC, 0xFFB89C78, 0xFF2E7A5A, 0xFFD0C0A0};
                shopWalls = new int[]{0xFFE0A070, 0xFF2E7A5A, 0xFFD88A5A, 0xFF5A8AC0, 0xFFF2EEE4, 0xFFC86A4A};
                apartmentWalls = new int[]{0xFFF2EEE4, 0xFFE8C890, 0xFFE0A070, 0xFFD8D0C0, 0xFFE8E0D0};
                cruiserBody = 0xFF7A8A7A;
                cruiserDoor = 0xFFF2F2F2;
                cruiserRoof = 0;
                cruiserChecks = false;
                streets = new String[]{"Mohammed V", "Hassan II", "des FAR", "Zerktouni", "Moulay Youssef",
                        "Moulay Ismail", "Ibn Battouta", "Ibn Sina", "Al Massira", "Allal Ben Abdellah", "de la Liberté",
                        "Abdelkrim Khattabi", "Al Mansour", "Yacoub El Mansour", "Bab Doukkala", "Derb Dabachi",
                        "Riad Zitoun", "Dar El Bacha", "Fès", "Marrakech", "Tanger", "Rabat", "Agadir", "Oujda", "Meknès",
                        "Atlas", "des Orangers", "des Palmiers", "Jemaa", "Kasbah", "Souk", "Mellah", "Andalous",
                        "Al Qods", "Annakhil", "Oued", "Sahara", "Ziz", "Sebou", "Moulouya", "Al Wahda", "Ennasr",
                        "Al Amal", "Essalam", "Al Fath", "Ibn Khaldoun", "Averroès", "Al Idrissi", "Tarik Ibn Ziad",
                        "Al Andalous"};
                mainWords = new String[]{"Avenue", "Boulevard", "Avenue", "Route"};
                localWords = new String[]{"Rue", "Derb", "Rue", "Zankat", "Derb", "Impasse"};
                typeFirst = true;
                ringRoad = "Rocade";
                townStart = new String[]{"Aït ", "Sidi ", "Ain ", "Bab ", "Ksar ", "Dar ", "Oued ", "Tizi ", "Beni ",
                        "Moulay ", "Ouled ", "Bir "};
                townEnd = new String[]{"Benhaddou", "Ifrane", "Slimane", "Kacem", "Bouzid", "Taourirt", "Zitoun",
                        "Mellal", "Bennour", "Larbi", "Sebaa", "Amar", "Yacoub", "Kerma"};
                districtWords = new String[]{"Gueliz", "Hivernage", "Mellah", "Kasbah", "Agdal", "Riad", "Souissi",
                        "Maarif", "Anfa", "Bourgogne", "Hassan", "Ocean", "Palmeraie", "Bab Doukkala", "Daoudiate",
                        "Massira", "Sidi Youssef", "Mouassine", "Zitoun", "Andalous"};
                hamletEnds = new String[]{" Douar", " Ksar", " Kasbah", " Oasis", " Tighremt", " Agadir", " Ain", " Souk"};
                churches = new String[]{"Mosquée Hassan II", "Mosquée Koutoubia", "Mosquée de la Kasbah", "Mosquée Ben Youssef",
                        "Mosquée Al Atiqa", "Mosquée Moulay Idriss", "Église Notre-Dame", "Mosquée Bab Doukkala"};
                schools = new String[]{"Lycée Mohammed V", "École Ibn Sina", "Collège Al Massira", "Lycée Hassan II",
                        "École Al Andalous", "Lycée Victor Hugo"};
                markets = new String[]{"Marjene", "Acima", "Label'Vie", "BIM", "Carrefour Market", "Souk El Had"};
                pharmacies = new String[]{"Pharmacie Centrale", "Pharmacie de la Médina", "Pharmacie Al Amal", "Pharmacie du Souk"};
                gunStores = new String[]{"Armurerie du Maroc", "Chasse Atlas", "Armurerie Al Fath", "Sport et Chasse Ifrane"};
                bases = new String[]{"Caserne Moulay Ismail", "Base de Benguerir", "Caserne Al Massira", "Camp Kénitra",
                        "Caserne Hassan II", "Base de Meknès"};
                hospital = "Hôpital Ibn Rochd";
                policeStation = "Commissariat ";
                fireStation = "Protection Civile ";
                first = new String[]{"Mohamed", "Fatima", "Youssef", "Khadija", "Amine", "Salma", "Omar", "Aya",
                        "Hamza", "Imane", "Mehdi", "Zineb", "Ayoub", "Nour", "Karim", "Meryem", "Rachid", "Hanane",
                        "Said", "Laila", "Adil", "Samira", "Anas", "Houda", "Ilyas", "Sara", "Hicham", "Nadia", "Yassine", "Ghita"};
                last = new String[]{"Alaoui", "Benali", "El Amrani", "Bennani", "Tazi", "Idrissi", "El Fassi", "Berrada",
                        "Cherkaoui", "Chraibi", "Lahlou", "Ouazzani", "Benjelloun", "Saidi", "El Mansouri", "Kettani",
                        "Bouzidi", "Hajji", "Naciri", "Sebti", "Filali", "Zniber", "Amrani", "Lamrani", "Rifai", "Guessous"};
                dogs = new String[]{"Simba", "Rex", "Lucky", "Rocky", "Max", "Luna", "Kira", "Zorro", "Nala", "Tiger",
                        "Leo", "Bella", "Sahara", "Atlas", "Choco", "Lion"};
                carModels = new int[]{0, 0, 4, 4, 4, 1, 5, 2, 6, 6, 7};
                carColors = new int[]{0xFFF2F2F2, 0xFFE8E8E8, 0xFF8A8F96, 0xFF2A2C30, 0xFFB03A2E, 0xFFE8E0D0, 0xFF5A6E8A};
                taxiColor = 0xFFE03A3A;
                busColor = 0xFFE8E0C8;
                pavement = 0xFFC8A880;
                grass = 0xFF8A8A4A;
                plaza = 0xFFD8B890;
                officeRoofs = new int[]{0xFFD8CCB4, 0xFFC8B494, 0xFFE0D4BC, 0xFFB8BCC0, 0xFFB89C78};
                officeWalls = new int[]{0xFFF2EEE4, 0xFFE8C890, 0xFFE0A070, 0xFFDCD6C8, 0xFFD8D0C0};
                oldWalls = new int[]{0xFFD88A5A, 0xFFE0A070, 0xFFC86A4A, 0xFFE8C890, 0xFFB86040};
                oldRoofs = new int[]{0xFFD8CCB4, 0xFF2E7A5A, 0xFFC8B494, 0xFFB89C78};
                look = MEXICO;
                churchLook = MEXICO;
                shieldColor = 0xFF1E4AA8;
                break;
            case RUSSIA:
                leftHand = false;
                centreLine = 0xFFE8E8E4;
                houseRoofs = new int[]{0xFF3F6A4A, 0xFF8A3A2E, 0xFF4E5560, 0xFF5A7A9A, 0xFF7A5A48, 0xFF6A7078};
                houseWalls = new int[]{0xFFC89A6A, 0xFFB8D0C0, 0xFFE8D8A0, 0xFFB8C8E0, 0xFF8A6A4A, 0xFFE0C8C0};
                shopRoofs = new int[]{0xFF6A7078, 0xFF8A9096, 0xFF4E5560, 0xFF3F6A4A, 0xFF5A6670, 0xFF8A3A2E};
                shopWalls = new int[]{0xFFE8D8A0, 0xFFB8D0C0, 0xFFE0C8C0, 0xFFB8C8E0, 0xFFF2EEE4, 0xFFC8504A};
                apartmentWalls = new int[]{0xFFE8D8A0, 0xFFD8D4CC, 0xFFC8C4BC, 0xFFE0C8C0, 0xFFB8C8E0};
                cruiserBody = 0xFFF2F2F2;
                cruiserDoor = 0xFF1F4FA8;
                cruiserRoof = 0;
                cruiserChecks = false;
                streets = new String[]{"Lenina", "Pushkina", "Gagarina", "Mira", "Sovetskaya", "Pobedy", "Tverskaya",
                        "Arbat", "Nevsky", "Sadovaya", "Lermontova", "Tolstogo", "Chekhova", "Gorkogo", "Kirova",
                        "Lomonosova", "Mendeleeva", "Kosmonavtov", "Molodezhnaya", "Shkolnaya", "Zavodskaya",
                        "Vokzalnaya", "Naberezhnaya", "Tsentralnaya", "Sportivnaya", "Lesnaya", "Polevaya", "Rechnaya",
                        "Sirenevaya", "Berezovaya", "Dachnaya", "Novaya", "Krasnaya", "Oktyabrskaya", "Pervomayskaya",
                        "Komsomolskaya", "Stroiteley", "Zhukova", "Suvorova", "Kutuzova", "Dostoevskogo", "Turgeneva",
                        "Gogolya", "Tchaikovskogo", "Glinki", "Vernadskogo", "Leninsky", "Moskovskaya", "Kalinina", "Mayakovskogo"};
                mainWords = new String[]{" Prospekt", " Shosse", " Prospekt", " Bulvar"};
                localWords = new String[]{" Ulitsa", " Ulitsa", " Ulitsa", " Pereulok", " Proyezd", " Tupik"};
                typeFirst = false;
                ringRoad = "Koltsevaya";
                townStart = new String[]{"Novo", "Staro", "Krasno", "Belo", "Zelen", "Pervo", "Sever", "Yuzhno",
                        "Kamensk", "Ozer", "Les", "Bor", "Volgo", "Dnepro", "Ural", "Zavod"};
                townEnd = new String[]{"grad", "sk", "ovo", "ino", "gorsk", "dar", "polye", "ovka", "evka", "slavl",
                        "gorod", "yar", "ozersk", "sibirsk"};
                districtWords = new String[]{"Tsentralny", "Sovetsky", "Leninsky", "Oktyabrsky", "Kirovsky",
                        "Zavodskoy", "Pervomaysky", "Severny", "Yuzhny", "Vostochny", "Zapadny", "Kuntsevo", "Sokol",
                        "Arbat", "Kitay-gorod", "Basmanny", "Khamovniki", "Presnya", "Zamoskvorechye", "Tagansky"};
                hamletEnds = new String[]{"ovka", " Dachi", "ino", " Khutor", "evo", " Selo", " Derevnya", " Posyolok"};
                churches = new String[]{"Cathedral of Christ the Saviour", "Church of the Intercession", "St Nicholas Church",
                        "Kazan Cathedral", "Church of the Resurrection", "Uspensky Cathedral", "Trinity Church",
                        "Church of St Elijah"};
                schools = new String[]{"School No. 1", "School No. 17", "Gymnasium No. 5", "Lyceum No. 2",
                        "School No. 44", "Cadet School"};
                markets = new String[]{"Pyaterka", "Magnet", "Perekryostok", "Diksi", "Lenta", "Rynok"};
                pharmacies = new String[]{"Apteka 36.6", "Rigla", "Apteka Zdorovye", "Gorzdrav"};
                gunStores = new String[]{"Okhotnik", "Kalashnikov Shop", "Oruzheynaya Lavka", "Sibir Hunting"};
                bases = new String[]{"Kantemirovskaya Base", "Alabino", "Kubinka", "Taman Garrison", "Mulino", "Pechenga"};
                hospital = "City Hospital No. 1";
                policeStation = "Police Department ";
                fireStation = "Fire Station No. ";
                first = new String[]{"Aleksandr", "Anastasia", "Dmitry", "Maria", "Maksim", "Anna", "Sergey", "Elena",
                        "Ivan", "Olga", "Mikhail", "Tatiana", "Andrey", "Natalia", "Nikolai", "Ekaterina", "Alexei",
                        "Irina", "Vladimir", "Svetlana", "Artyom", "Polina", "Pavel", "Yulia", "Igor", "Daria", "Oleg",
                        "Ksenia", "Yuri", "Vera"};
                last = new String[]{"Ivanov", "Smirnov", "Kuznetsov", "Popov", "Vasiliev", "Petrov", "Sokolov",
                        "Mikhailov", "Novikov", "Fedorov", "Morozov", "Volkov", "Alekseev", "Lebedev", "Semenov",
                        "Egorov", "Pavlov", "Kozlov", "Stepanov", "Nikolaev", "Orlov", "Andreev", "Makarov", "Zaitsev",
                        "Solovyov", "Borisov"};
                dogs = new String[]{"Sharik", "Druzhok", "Belka", "Strelka", "Laika", "Bobik", "Tuzik", "Zhuchka",
                        "Rex", "Muhtar", "Polkan", "Naida", "Barbos", "Dina", "Grom", "Ryzhik"};
                carModels = new int[]{0, 0, 0, 4, 4, 2, 2, 1, 5, 8, 6, 7};
                carColors = new int[]{0xFFF2F2F2, 0xFF2A2C30, 0xFF8A8F96, 0xFF3C6A4E, 0xFF8A3A2E, 0xFFB8BCC0, 0xFF2E4F8A, 0xFFE8D8A0};
                taxiColor = 0xFFE8C21A;
                busColor = 0xFFE8E8E8;
                pavement = 0xFF9A9894;
                grass = 0xFF5E7A3E;
                officeRoofs = new int[]{0xFF6A7078, 0xFF8A9096, 0xFF9AA2AA, 0xFF5A6670, 0xFF4E5560};
                officeWalls = new int[]{0xFFE8D8A0, 0xFFD8D4CC, 0xFFC8C4BC, 0xFFB8C8E0, 0xFFE0C8C0};
                oldWalls = new int[]{0xFFE8D8A0, 0xFFB8D0C0, 0xFFE0C8C0, 0xFFC8504A, 0xFFB8C8E0};
                oldRoofs = new int[]{0xFF3F6A4A, 0xFF8A3A2E, 0xFF6A7078, 0xFF4E5560};
                look = USA;
                churchLook = MEXICO;
                shieldColor = 0xFF1E4AA8;
                break;
            default:
                leftHand = false;
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
                typeFirst = false;
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
                break;
        }
        if (id <= MEXICO) {
            look = id;
            churchLook = id == JAPAN || id == FRANCE || id == MEXICO ? id : -1;
            shieldColor = id == USA ? 0xFF1E3A8A : id == FRANCE ? 0xFFB02020 : 0xFF1E6A3A;
        }
        agencies();
    }

    /** Highways the country might have, as {name, what's on the signs}. */
    String[][] highways;
    /**
     * Who polices the highway, and (in the USA) the countryside: the force's name, what an officer is called on
     * the radio, and their car's body and door colours. ruralName is null where the police cover the country too.
     */
    String hpName, hpShort, ruralName, ruralShort;
    int hpBody, hpDoor, hpShirt, ruralBody, ruralDoor, ruralShirt;

    private void agencies() {
        switch (id) {
            case AUSTRALIA:
                highways = new String[][]{{"Hume Highway", "M31"}, {"Pacific Motorway", "M1"}, {"Bruce Highway", "A1"}, {"Princes Highway", "A1"}};
                hpName = "Highway Patrol";
                hpShort = "Highway";
                hpBody = 0xFFF2F2F2;
                hpDoor = 0xFFE8B020;
                hpShirt = 0xFF3A5A8A;
                break;
            case JAPAN:
                highways = new String[][]{{"Tomei Expressway", "E1"}, {"Chuo Expressway", "E20"}, {"Tohoku Expressway", "E4"}};
                hpName = "Expressway Police";
                hpShort = "Expressway";
                hpBody = 0xFF202224;
                hpDoor = 0xFFF2F2F2;
                hpShirt = 0xFF2E3E6A;
                break;
            case FRANCE:
                highways = new String[][]{{"Autoroute A6", "A6"}, {"Autoroute A7", "A7"}, {"Autoroute A10", "A10"}};
                hpName = "Gendarmerie";
                hpShort = "Gendarme";
                hpBody = 0xFF1E2E5A;
                hpDoor = 0xFFF2F2F2;
                hpShirt = 0xFF1E2E5A;
                break;
            case MEXICO:
                highways = new String[][]{{"Carretera Federal 15", "MEX 15"}, {"Autopista 57D", "MEX 57D"}, {"Carretera Federal 85", "MEX 85"}};
                hpName = "Guardia Nacional";
                hpShort = "Guardia";
                hpBody = 0xFFF2F2F2;
                hpDoor = 0xFF5A1E2E;
                hpShirt = 0xFF4A4A3A;
                break;
            case SWITZERLAND:
                highways = new String[][]{{"Autobahn A1", "A1"}, {"Autobahn A2", "A2"}, {"Autobahn A3", "A3"}};
                hpName = "Autobahnpolizei";
                hpShort = "Autobahn";
                hpBody = 0xFFF2F2F2;
                hpDoor = 0xFFE87A1A;
                hpShirt = 0xFF2E3A4A;
                break;
            case KOREA:
                highways = new String[][]{{"Gyeongbu Expressway", "1"}, {"Seohaean Expressway", "15"}, {"Yeongdong Expressway", "50"}};
                hpName = "Expressway Patrol";
                hpShort = "Expressway";
                hpBody = 0xFFF2F2F2;
                hpDoor = 0xFF1F4FA8;
                hpShirt = 0xFF2E3E6A;
                break;
            case MALAYSIA:
                highways = new String[][]{{"North-South Expressway", "E1"}, {"East Coast Expressway", "E8"}, {"Karak Highway", "E8"}};
                hpName = "Traffic Police";
                hpShort = "Trafik";
                hpBody = 0xFFF2F2F2;
                hpDoor = 0xFF1F3F8A;
                hpShirt = 0xFF2E3A6A;
                break;
            case PORTUGAL:
                highways = new String[][]{{"Autoestrada A1", "A1"}, {"Autoestrada A2", "A2"}, {"Autoestrada A25", "A25"}};
                hpName = "GNR Trânsito";
                hpShort = "GNR";
                hpBody = 0xFF2E4A3A;
                hpDoor = 0xFFF2F2F2;
                hpShirt = 0xFF3A4A3A;
                break;
            case MOROCCO:
                highways = new String[][]{{"Autoroute A1", "A1"}, {"Autoroute A3", "A3"}, {"Autoroute A7", "A7"}};
                hpName = "Gendarmerie Royale";
                hpShort = "Gendarme";
                hpBody = 0xFF3A5A8A;
                hpDoor = 0xFFF2F2F2;
                hpShirt = 0xFF3A4A6A;
                break;
            case RUSSIA:
                highways = new String[][]{{"M-11 Neva", "M-11"}, {"M-4 Don", "M-4"}, {"M-7 Volga", "M-7"}};
                hpName = "DPS";
                hpShort = "DPS";
                hpBody = 0xFFF2F2F2;
                hpDoor = 0xFF1F4FA8;
                hpShirt = 0xFF4A4E56;
                break;
            default:
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
                break;
        }
    }
}
