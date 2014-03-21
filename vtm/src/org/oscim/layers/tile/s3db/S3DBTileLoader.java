package org.oscim.layers.tile.s3db;

import org.oscim.backend.canvas.Color;
import org.oscim.core.GeometryBuffer.GeometryType;
import org.oscim.core.MapElement;
import org.oscim.core.MercatorProjection;
import org.oscim.layers.tile.MapTile;
import org.oscim.layers.tile.TileLoader;
import org.oscim.layers.tile.TileManager;
import org.oscim.renderer.elements.ElementLayers;
import org.oscim.renderer.elements.ExtrusionLayer;
import org.oscim.tiling.ITileDataSource;
import org.oscim.tiling.TileSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class S3DBTileLoader extends TileLoader {
	static final Logger log = LoggerFactory.getLogger(S3DBTileLoader.class);

	/** current TileDataSource used by this MapTileLoader */
	private ITileDataSource mTileDataSource;

	private ExtrusionLayer mLayers;
	private ExtrusionLayer mRoofs;

	private float mGroundScale;

	public S3DBTileLoader(TileManager tileManager, TileSource tileSource) {
		super(tileManager);
		mTileDataSource = tileSource.getDataSource();
	}

	@Override
	public void cleanup() {
		mTileDataSource.destroy();
	}

	@Override
	protected boolean loadTile(MapTile tile) {
		mTile = tile;

		double lat = MercatorProjection.toLatitude(tile.y);
		mGroundScale = (float) MercatorProjection
		    .groundResolution(lat, 1 << mTile.zoomLevel);

		mLayers = new ExtrusionLayer(0, mGroundScale, Color.get(255, 254, 252));
		//mRoofs = new ExtrusionLayer(0, mGroundScale, Color.get(207, 209, 210));
		mRoofs = new ExtrusionLayer(0, mGroundScale, Color.get(247, 249, 250));
		mLayers.next = mRoofs;

		ElementLayers layers = new ElementLayers();
		layers.setExtrusionLayers(mLayers);
		tile.data = layers;

		try {
			/* query database, which calls process() callback */
			mTileDataSource.query(mTile, this);
		} catch (Exception e) {
			log.debug("{}", e);
			return false;
		}

		return true;
	}

	String COLOR_KEY = "c";
	String ROOF_KEY = "roof";
	String ROOF_SHAPE_KEY = "roof:shape";

	@Override
	public void process(MapElement element) {
		//log.debug("TAG {}", element.tags);
		if (element.type != GeometryType.TRIS) {
			log.debug("wrong type " + element.type);
			return;
		}
		boolean isRoof = element.tags.containsKey(ROOF_KEY);

		int c = 0;
		if (element.tags.containsKey(COLOR_KEY)) {
			c = S3DBLayer.getColor(element.tags.getValue(COLOR_KEY), isRoof);
		}

		if (c == 0) {
			String roofShape = element.tags.getValue(ROOF_SHAPE_KEY);

			if (isRoof && (roofShape == null || "flat".equals(roofShape)))
				mRoofs.add(element);
			else
				mLayers.add(element);
			return;
		}

		for (ExtrusionLayer l = mLayers; l != null; l = (ExtrusionLayer) l.next) {
			if (l.color == c) {
				l.add(element);
				return;
			}
		}
		ExtrusionLayer l = new ExtrusionLayer(0, mGroundScale, c);

		l.next = mRoofs.next;
		mRoofs.next = l;

		l.add(element);
	}

	@Override
	public void completed(QueryResult result) {
		mLayers = null;
		mRoofs = null;
		super.completed(result);
	}
}
