package com.instantaxtion.zombiesandbox;

import java.util.Random;

/**
 * Where the city is: the country sets the look of its buildings, its street, town and people's names, the
 * road markings, the police cars and which side of the road traffic keeps to.
 */
final class Country {
    static final int USA = 0, AUSTRALIA = 1, JAPAN = 2, FRANCE = 3, MEXICO = 4;
    static final String[] NAMES = {"USA", "Australia", "Japan", "France", "Mexico"};
    static final String[] INFO = {
            "Wide avenues with yellow centre lines, clapboard houses and black-and-white cruisers.",
            "Brick and weatherboard homes under tile and Colorbond roofs, dry grass, chequered police cars. Traffic drives on the left.",
            "Pale walls and blue-grey tiled roofs, temples and shrines, black-and-white patrol cars. Traffic drives on the left.",
            "Cream stone and zinc roofs, rues and boulevards, white and blue police cars.",
            "Bright painted walls and flat roofs, calles and avenidas, blue and white patrol cars."};

    /** The country of the game being played (for people's names). */
    static volatile Country current;

    final int id;
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
    int[] officeRoofs, officeWalls, oldWalls, oldRoofs;

    private static final Country[] ALL = new Country[NAMES.length];

    static Country get(int id) {
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
        return typeFirst ? type + " " + base : id == JAPAN ? base + type : base + " " + type;
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
                pavement = 0xFFB49C80;
                grass = 0xFF77803F;
                plaza = 0xFFC8A880;
                officeRoofs = new int[]{0xFFCFC4B0, 0xFFC0B49C, 0xFFD8CEBA, 0xFFB8AC96, 0xFFC8BCA6, 0xFFB4583A};
                officeWalls = new int[]{0xFFF2EEE4, 0xFFF0C04A, 0xFFE8873A, 0xFF4FB8B0, 0xFFE07A8C, 0xFFE2DAC8};
                oldWalls = new int[]{0xFFE07A8C, 0xFFF0C04A, 0xFF4FB8B0, 0xFFE8873A, 0xFF7A9AD8, 0xFFC8503A};
                oldRoofs = new int[]{0xFFB4583A, 0xFFA04A30, 0xFFC9BFAE, 0xFFBE6A44};
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
    }
}
