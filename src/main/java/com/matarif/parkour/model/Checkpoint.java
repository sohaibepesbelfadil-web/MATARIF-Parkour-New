package com.matarif.parkour.model;

/** A numbered course checkpoint stored as a plain location string so data loads before world access. */
public final class Checkpoint {
    private int number;
    private String location;

    public Checkpoint() { }

    public Checkpoint(int number, String location) {
        this.number = number;
        this.location = location;
    }

    public int getNumber() { return number; }
    public String getLocation() { return location; }
}

