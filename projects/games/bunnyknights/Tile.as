package {

import flash.display.Bitmap;
import flash.display.BitmapData;
import flash.display.Sprite;


public class Tile
{
    public static const LAYER_BACK :int = 0;
    public static const LAYER_ACTION_BACK :int = 1;
    public static const LAYER_MIDDLE :int = 2;
    public static const LAYER_ACTION_MIDDLE :int = 3;
    public static const LAYER_FRONT :int = 4;
    public static const LAYER_ACTION_FRONT :int = 5;
    public static const LAYER_HUD :int = 6;

    public static const TYPE_BRICK :int = 0;
    public static const TYPE_BLACK :int = 1;
    public static const TYPE_LADDER :int = 2;

    public static const EFFECT_NONE :int = 0;
    public static const EFFECT_SOLID :int = 1;
    public static const EFFECT_LADDER :int = 2;

    public static const TILE_SIZE :int = 32;
    
    public var x :int, y :int;
    public var type :int;
    public var layer :int;
    public var effect :int;

    public static function Brick (x :int, y :int) :Tile
    {
        return new Tile(x, y);
    }

    public static function Ladder (x :int, y :int) :Tile
    {
        return new Tile(x, y, TYPE_LADDER, LAYER_FRONT, EFFECT_LADDER);
    }

    public function Tile (x :int, y :int, 
        type :int = TYPE_BRICK, layer :int = LAYER_MIDDLE, 
        effect :int = EFFECT_SOLID)
    {
        this.x = x;
        this.y = y;
        this.layer = layer;
        this.type = type;
        this.effect = effect;
    }

    public function getSprite () :Sprite
    {
        if (_sprite == null) {
            _sprite = new Sprite();
            _sprite.addChild(getBitmap());
        }
        return _sprite;
    }

    public function getBitmapData () :BitmapData
    {
        return getBitmap().bitmapData;
    }

    protected function getBitmap () :Bitmap
    {
        switch (type) {
          case TYPE_BRICK:
            return Bitmap(new blueTileAsset());
          case TYPE_BLACK:
            return Bitmap(new blackTileAsset());
          case TYPE_LADDER:
            return Bitmap(new ladderTileAsset());
          default:
            return Bitmap(new blueTileAsset());
        }
    }

    protected var _sprite :Sprite;

    [Embed(source="rsrc/blue_tile.gif")]
    protected var blueTileAsset :Class;

    [Embed(source="rsrc/black_tile.gif")]
    protected var blackTileAsset :Class;

    [Embed(source="rsrc/ladder_middle.gif")]
    protected var ladderTileAsset :Class;
}
}
