package fr.epistudio.epysia.assets;

public abstract class Asset {

    private final AssetType type;

    public Asset(AssetType type){
        this.type = type;
    }

    public AssetType getType(){
        return this.type;
    }

}
