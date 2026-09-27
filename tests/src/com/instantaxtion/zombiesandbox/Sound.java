package com.instantaxtion.zombiesandbox;

/** Silent stand-in for the real Sound (which needs Android's audio classes) so the game runs on a desktop JVM. */
final class Sound {
    Sound(android.content.Context c) {
    }

    void play(int id, float volume, float pan) {
    }

    void setVolumes(float music, float sfx) {
    }

    void playTrack(int track) {
    }

    void pause() {
    }

    void resume() {
    }

    void release() {
    }
}
