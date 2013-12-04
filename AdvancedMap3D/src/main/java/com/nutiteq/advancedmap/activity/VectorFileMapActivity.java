package com.nutiteq.advancedmap.activity;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ZoomControls;

import com.nutiteq.MapView;
import com.nutiteq.advancedmap.R;
import com.nutiteq.advancedmap.R.drawable;
import com.nutiteq.advancedmap.R.id;
import com.nutiteq.advancedmap.R.layout;
import com.nutiteq.components.Bounds;
import com.nutiteq.components.Components;
import com.nutiteq.components.Envelope;
import com.nutiteq.components.MapPos;
import com.nutiteq.components.Options;
import com.nutiteq.filepicker.FilePickerActivity;
import com.nutiteq.layers.raster.TMSMapLayer;
import com.nutiteq.layers.vector.OgrHelper;
import com.nutiteq.layers.vector.OgrLayer;
import com.nutiteq.log.Log;
import com.nutiteq.projections.EPSG3857;
import com.nutiteq.projections.Projection;
import com.nutiteq.style.LabelStyle;
import com.nutiteq.style.LineStyle;
import com.nutiteq.style.PointStyle;
import com.nutiteq.style.PolygonStyle;
import com.nutiteq.style.StyleSet;
import com.nutiteq.utils.UnscaledBitmapLoader;

/**
 * 
 * Demonstrates usage of OgrLayer - offline vector layer which uses OGR library
 *
 * 
 * It requries OGR native library with JNI wrappers.
 * 
 * To use the sample an OGR-supported datasource, e.g. Shapefile must be in SDCard
 * 
 * See https://github.com/nutiteq/hellomap3d/wiki/Ogr-layer for details
 * 
 * @author jaak
 *
 */
public class VectorFileMapActivity extends Activity implements FilePickerActivity {

	private static final int DIALOG_TABLE_LIST = 1;
	
    private MapView mapView;

    private OgrLayer ogrLayer;

    private String[] tableList;

    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// spinner in status bar, for progress indication
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		setContentView(R.layout.main);

		// enable logging for troubleshooting - optional
		Log.enableAll();
		Log.setTag("ogractivity");

		// 1. Get the MapView from the Layout xml - mandatory
		mapView = (MapView) findViewById(R.id.mapView);

		// Optional, but very useful: restore map state during device rotation,
		// it is saved in onRetainNonConfigurationInstance() below
		Components retainObject = (Components) getLastNonConfigurationInstance();
		if (retainObject != null) {
			// just restore configuration, skip other initializations
			mapView.setComponents(retainObject);
			return;
		} else {
			// 2. create and set MapView components - mandatory
		    Components components = new Components();
		    // set stereo view: works if you rotate to landscape and device has HTC 3D or LG Real3D
		    mapView.setComponents(components);
		}


		// 3. Define map layer for basemap - mandatory.
		// Here we use MapQuest open tiles
		// Almost all online tiled maps use EPSG3857 projection.
		TMSMapLayer mapLayer = new TMSMapLayer(new EPSG3857(), 0, 18, 0,
				"http://otile1.mqcdn.com/tiles/1.0.0/osm/", "/", ".png");

		mapView.getLayers().setBaseLayer(mapLayer);

		// set initial map view camera - optional. "World view" is default
		// Location: Estonia
        mapView.setFocusPoint(mapView.getLayers().getBaseLayer().getProjection().fromWgs84(24.5f, 58.3f));

		// rotation - 0 = north-up
		mapView.setRotation(0f);
		// zoom - 0 = world, like on most web maps
		mapView.setZoom(10.0f);
        // tilt means perspective view. Default is 90 degrees for "normal" 2D map view, minimum allowed is 30 degrees.
		mapView.setTilt(90.0f);

		// Activate some mapview options to make it smoother - optional
		mapView.getOptions().setPreloading(false);
		mapView.getOptions().setSeamlessHorizontalPan(true);
		mapView.getOptions().setTileFading(false);
		mapView.getOptions().setKineticPanning(true);
		mapView.getOptions().setDoubleClickZoomIn(true);
		mapView.getOptions().setDualClickZoomOut(true);

		// set sky bitmap - optional, default - white
		mapView.getOptions().setSkyDrawMode(Options.DRAW_BITMAP);
		mapView.getOptions().setSkyOffset(4.86f);
		mapView.getOptions().setSkyBitmap(
				UnscaledBitmapLoader.decodeResource(getResources(),
						R.drawable.sky_small));

        // Map background, visible if no map tiles loaded - optional, default - white
		mapView.getOptions().setBackgroundPlaneDrawMode(Options.DRAW_BITMAP);
		mapView.getOptions().setBackgroundPlaneBitmap(
				UnscaledBitmapLoader.decodeResource(getResources(),
						R.drawable.background_plane));
		mapView.getOptions().setClearColor(Color.WHITE);

		// configure texture caching - optional, suggested
		mapView.getOptions().setTextureMemoryCacheSize(20 * 1024 * 1024);
		mapView.getOptions().setCompressedMemoryCacheSize(8 * 1024 * 1024);

        // define online map persistent caching - optional, suggested. Default - no caching
        mapView.getOptions().setPersistentCachePath(this.getDatabasePath("mapcache").getPath());
		// set persistent raster cache limit to 100MB
		mapView.getOptions().setPersistentCacheSize(100 * 1024 * 1024);

		// 4. zoom buttons using Android widgets - optional
		// get the zoomcontrols that was defined in main.xml
		ZoomControls zoomControls = (ZoomControls) findViewById(R.id.zoomcontrols);
		// set zoomcontrols listeners to enable zooming
		zoomControls.setOnZoomInClickListener(new View.OnClickListener() {
			public void onClick(final View v) {
				mapView.zoomIn();
			}
		});
		zoomControls.setOnZoomOutClickListener(new View.OnClickListener() {
			public void onClick(final View v) {
				mapView.zoomOut();
			}
		});
		
		 // read filename from extras
        Bundle b = getIntent().getExtras();
        String file = b.getString("selectedFile");
        
        addOgrLayer(mapLayer.getProjection(), file, null, Color.BLUE);
        
	}

    @Override
    protected void onStart() {
        mapView.startMapping();
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.stopMapping();
    }

    private void addOgrLayer(Projection proj, String dbPath, String table, int color) {
        // set styles for all 3 object types: point, line and polygon
        int minZoom = 5;

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float dpi = metrics.density;
        
        StyleSet<PointStyle> pointStyleSet = new StyleSet<PointStyle>();
        Bitmap pointMarker = UnscaledBitmapLoader.decodeResource(getResources(), R.drawable.point);
        PointStyle pointStyle = PointStyle.builder().setBitmap(pointMarker).setSize(0.05f).setColor(color).setPickingSize(0.2f).build();
        pointStyleSet.setZoomStyle(minZoom, pointStyle);

        StyleSet<LineStyle> lineStyleSet = new StyleSet<LineStyle>();
        LineStyle lineStyle = LineStyle.builder().setWidth(0.05f).setColor(color).build();
        lineStyleSet.setZoomStyle(minZoom, lineStyle);

        PolygonStyle polygonStyle = PolygonStyle.builder().setColor(color & 0x80FFFFFF).setLineStyle(lineStyle).build();
        StyleSet<PolygonStyle> polygonStyleSet = new StyleSet<PolygonStyle>(null);
        polygonStyleSet.setZoomStyle(minZoom, polygonStyle);

        LabelStyle labelStyle = 
                LabelStyle.builder()
                       .setEdgePadding((int) (12 * dpi))
                       .setLinePadding((int) (6 * dpi))
                       .setTitleFont(Typeface.create("Arial", Typeface.BOLD), (int) (16 * dpi))
                       .setDescriptionFont(Typeface.create("Arial", Typeface.NORMAL), (int) (13 * dpi))
                       .build();
        
        try {
            ogrLayer = new OgrLayer(proj, dbPath, table, 500, pointStyleSet, lineStyleSet, polygonStyleSet, labelStyle);
            tableList = ogrLayer.getTableList();
            if(table == null && tableList != null){
                // show layer/table selector if there are more than 1 layer
                if(tableList.length > 1){
                    showDialog(DIALOG_TABLE_LIST);
                }else{
                    addOgrTable(0);
                }
            }
            
        } catch (IOException e) {
            Log.error(e.getLocalizedMessage());
            Toast.makeText(this, "ERROR "+e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }

        

        
    }
	
    public MapView getMapView() {
        return mapView;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch(id){

        case DIALOG_TABLE_LIST:
            return new AlertDialog.Builder(this)
            .setTitle("Select table:")
            .setSingleChoiceItems(tableList, 0, null)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                    int selectedPosition = ((AlertDialog) dialog)
                            .getListView().getCheckedItemPosition();
                    addOgrTable(selectedPosition);
                }
            }).create();
            
        }
        return null;
    }
    
    protected void addOgrTable(int selectedPosition) {

        mapView.getLayers().addLayer(ogrLayer);
        String[] tableKey = tableList[selectedPosition].split(OgrHelper.TABLE_SEPARATOR);
        String selectedTable = tableKey[0];
        ogrLayer.setTable(selectedTable);
        
        Envelope extent = ogrLayer.getDataExtent();
        if (extent != null) {
            mapView.setBoundingBox(new Bounds(extent.minX, extent.maxY,
                    extent.maxX, extent.minY), true);
        } 
        
    }

    @Override
    public String getFileSelectMessage() {
        return "Select vector data file (.shp, .kml etc)";
    }

    @Override
    public FileFilter getFileFilter() {
        return new FileFilter() {
            @Override
            public boolean accept(File file) {
                // accept any file, as number of possible extensions is big
                return true;
            }
        };
    }
    
}

