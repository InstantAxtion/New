package com.instantaxtion.zombiesandbox;

/** A list of named options, each choosing one of several values; shown as rows by {@link Menu}. */
interface OptionSet {
    int count();

    String label(int i);

    String[] values(int i);

    int get(int i);

    void set(int i, int value);
}
