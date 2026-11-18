package com.audiobookdownloader;

/** Project the cover's rotation around its spine onto the display. */
final class BookOpening {
    private BookOpening() {}

    static float coverWidth(float opening) {
        return (float) Math.cos(Math.PI * opening);
    }

    static float coverLift(float opening) {
        return (float) Math.sin(Math.PI * opening);
    }
}
