package com.d0klabs.cryptowalt.data;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Handler {
    Set protectedBlocks;
    Block catchBlock;
    Type type;
    
    public Handler(final Block catchBlock, final Type type) {
        this.protectedBlocks = new HashSet();
        this.catchBlock = catchBlock;
        this.type = type;
    }

    /**
     * Returns a <tt>Collection</tt> of the "try" blocks.
     */
    public Collection protectedBlocks() {
        return protectedBlocks;
    }

    public void setCatchBlock(final Block block) {
        catchBlock = block;
    }

    public Block catchBlock() {
        return catchBlock;
    }

    public Type catchType() {
        return type;
    }

    public String toString() {
        return "try -> catch (" + type + ") " + catchBlock;
    }
}

