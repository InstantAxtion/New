package com.instantaxtion.zombiesandbox;

/** Names for the people (and dogs) of the city, picked from each one's name seed. */
final class Names {
    private static final String[] FIRST = {"Maria", "James", "Aisha", "Tom", "Sofia", "Daniel", "Mei", "Carlos",
            "Emma", "Omar", "Grace", "Liam", "Priya", "Noah", "Zoe", "Ivan", "Hana", "Lucas", "Nora", "Sam", "Ruth",
            "Kwame", "Elena", "Ben", "Yuki", "Diego", "Chloe", "Marcus", "Leila", "Owen", "Anna", "Jamal", "Rosa",
            "Felix", "Ines", "Hugo", "Tara", "Victor", "Lena", "Eli"};
    private static final String[] LAST = {"Lopez", "Smith", "Khan", "Nguyen", "Brown", "Garcia", "Kim", "Okafor",
            "Miller", "Rossi", "Novak", "Silva", "Cohen", "Walker", "Tanaka", "Murphy", "Patel", "Jensen", "Moreau",
            "Haddad", "Clarke", "Santos", "Weber", "Reyes", "Hughes", "Ali", "Kowalski", "Park", "Diaz", "Stone"};
    private static final String[] DOGS = {"Rex", "Bella", "Max", "Luna", "Buddy", "Daisy", "Rocky", "Molly",
            "Scout", "Pepper", "Bear", "Rosie", "Duke", "Nala", "Biscuit", "Ziggy"};

    private Names() {
    }

    static String person(int seed) {
        int s = seed & 0x7FFFFFFF;
        return FIRST[s % FIRST.length] + " " + LAST[(s / FIRST.length) % LAST.length];
    }

    static String dog(int seed) {
        return DOGS[(seed & 0x7FFFFFFF) % DOGS.length];
    }
}
