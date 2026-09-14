package mekanism.common.item.interfaces;

import mekanism.api.gear.GearAttributeContext;

public interface IHasConditionalAttributes {

    /**
     * Called for each vanilla attribute query on an item implementing this interface.
     */
    void adjustAttributes(GearAttributeContext event);
}