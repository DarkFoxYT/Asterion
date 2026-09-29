package net.krodark.asterion.client.render;

 
public interface TextureCacheOwner {
    void asterion$setFrameCleanup(Runnable cleanup);
    void asterion$markUsed();
    long asterion$lastUse();
    void asterion$releaseFrameCache();
}
