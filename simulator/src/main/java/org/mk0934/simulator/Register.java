package org.mk0934.simulator;

/**
 * Created by Maciej Kumorek on 9/30/2014.
 */
public class Register {

    /**
     * Value held in the register
     */
    private int value;
    private boolean dirty;

    /**
     * Initializes the register
     */
    public Register() {
        this.value = 0x0;
    }

    public int getValue() {
        return this.value;
    }

    public void setValue(int newValue) {
        this.value = newValue;
        this.setDirty(true);
    }

    public boolean getDirty() {
        return this.dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
