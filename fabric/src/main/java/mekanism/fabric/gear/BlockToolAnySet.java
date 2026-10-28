package mekanism.fabric.gear;

import com.mojang.datafixers.util.Either;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Dynamic all-block set used by the two official Mekanism tool components. */
public final class BlockToolAnySet extends HolderSet.ListBacked<Block> {
    private final HolderLookup.RegistryLookup<Block> lookup;

    public BlockToolAnySet(HolderLookup.RegistryLookup<Block> lookup) {
        this.lookup = lookup;
    }

    public HolderLookup.RegistryLookup<Block> lookup() {
        return lookup;
    }

    @Override
    protected List<Holder<Block>> contents() {
        return lookup.listElements().map(holder -> (Holder<Block>) holder).toList();
    }

    @Override
    public boolean contains(Holder<Block> holder) {
        return holder.unwrapKey().flatMap(lookup::get).isPresent();
    }

    @Override
    public Either<TagKey<Block>, List<Holder<Block>>> unwrap() {
        return Either.right(contents());
    }

    @Override
    public Optional<TagKey<Block>> unwrapKey() {
        return Optional.empty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlockToolAnySet set && Objects.equals(lookup, set.lookup);
    }

    @Override
    public int hashCode() {
        return lookup.hashCode();
    }
}
