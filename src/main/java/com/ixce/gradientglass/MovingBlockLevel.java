package com.ixce.gradientglass;

import net.minecraft.client.multiplayer.ClientLevel;

// Lets a moving glass block see the world around it, so it renders like the glass it joins.
public interface MovingBlockLevel {
    void gradientglass$setLevel(ClientLevel level);
}
