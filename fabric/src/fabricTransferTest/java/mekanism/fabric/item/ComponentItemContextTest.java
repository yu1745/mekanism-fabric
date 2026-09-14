package mekanism.fabric.item;

import org.junit.jupiter.api.Test;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Checks the real native inventory transaction boundary; does not claim completion of the five domain handlers. */
class ComponentItemContextTest {
    @Test
    void detachedMutationNestedRollbackStaleGuardAndRealCount() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var slot = new SingleVariantStorage<ItemVariant>() {
            @Override protected ItemVariant getBlankVariant() { return ItemVariant.blank(); }
            @Override protected long getCapacity(ItemVariant variant) { return 64; }
        };
        try (var tx = Transaction.openOuter()) {
            require(slot.insert(ItemVariant.of(Items.STICK), 1, tx) == 1, "initial real slot insert");
            tx.commit();
        }
        var owner = new ComponentItemContext<ItemStack>(ContainerItemContext.ofSingleSlot(slot), Items.STICK,
              stack -> stack.getCount() == 1 ? stack : null);
        var original = slot.getResource();
        var edit = owner.read();
        require(edit != null, "single stack provider");
        edit.handler().set(DataComponents.DAMAGE, 12);
        require(slot.getResource().equals(original), "domain mutations detached from the real inventory");
        try (var tx = Transaction.openOuter()) {
            require(owner.exchange(edit, tx), "transactional container exchange");
            require(slot.getResource().toStack().getOrDefault(DataComponents.DAMAGE, 0) == 12, "visible within transaction");
        }
        require(slot.getResource().equals(original), "outer abort restores actual inventory components");
        try (var outer = Transaction.openOuter()) {
            var update = owner.read();
            update.handler().set(DataComponents.DAMAGE, 21);
            try (var nested = outer.openNested()) {
                require(owner.exchange(update, nested), "nested exchange");
                nested.commit();
            }
            require(!owner.exchange(edit, outer), "stale component decision cannot overwrite a new variant");
        }
        require(slot.getResource().equals(original), "nested commit remains owned by outer abort");
        try (var tx = Transaction.openOuter()) {
            require(slot.insert(original, 1, tx) == 1, "stack another container");
            tx.commit();
        }
        require(owner.read() == null, "provider sees actual context count, not find's synthesized count-one stack");
        System.out.println("TASK052_ITEM_CONTEXT_CHECK_PASS detached=true outer_nested_abort=true stale_guard=true actual_stack_count=true domain_handlers=NOT_TESTED");
    }
    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
