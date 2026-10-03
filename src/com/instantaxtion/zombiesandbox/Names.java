package com.instantaxtion.zombiesandbox;

/** Names for the people (and dogs) of the city, picked from each one's name seed. */
final class Names {
    private static Country here() {
        Country c = Country.current;
        return c != null ? c : Country.get(Country.USA);
    }

    private Names() {
    }

    static String person(int seed) {
        int s = seed & 0x7FFFFFFF;
        Country c = here();
        return c.first[s % c.first.length] + " " + c.last[(s / c.first.length) % c.last.length];
    }

    static String dog(int seed) {
        Country c = here();
        return c.dogs[(seed & 0x7FFFFFFF) % c.dogs.length];
    }
}
