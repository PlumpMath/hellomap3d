package com.nutiteq.layers.vector;

import java.io.IOException;

import org.gdal.ogr.ogr;

import com.nutiteq.components.Envelope;
import com.nutiteq.log.Log;
import com.nutiteq.projections.Projection;
import com.nutiteq.style.LabelStyle;
import com.nutiteq.style.LineStyle;
import com.nutiteq.style.PointStyle;
import com.nutiteq.style.PolygonStyle;
import com.nutiteq.style.StyleSet;
import com.nutiteq.tasks.Task;
import com.nutiteq.vectorlayers.GeometryLayer;

/**
 * Layer for reading OGR file data sources.
 *  
 * @author jaak
 *
 */
public class OgrLayer extends GeometryLayer {

    private float minZoom = Float.MAX_VALUE;
    private OgrHelper ogrHelper;

	static {
		ogr.RegisterAll();
	}
	
    static {
        try {
            // force Java to load PROJ.4 library. Needed as we don't call it directly, but 
            // OGR datasource reading may need it.
            System.loadLibrary("proj");
            
        } catch (Throwable t) {
            System.err.println("Unable to load proj: " + t);
        }
        }
    

	   protected class LoadOgrDataTask implements Task {
	        final Envelope envelope;
	        final int zoom;
	        
	        LoadOgrDataTask(Envelope envelope, int zoom) {
	          this.envelope = envelope;
	          this.zoom = zoom;
	        }
	        
	        @Override
	        public void run() {
	          setVisibleElementsList(ogrHelper.loadData(envelope, zoom));
	        }

	        @Override
	        public boolean isCancelable() {
	          return true;
	        }

	        @Override
	        public void cancel() {
	        }
	      }
	
	/**
	 * Open OGR datasource. Datasource properties depend on particular data type, e.g. for Shapefile just give file name
	 * This sample tries to read whole layer, you probably need adjustments to optimize reading depending on data specifics
	 * 
	 * @param proj layer projection. NB! data must be in the same projection
	 * @param fileName datasource name: file or connection string
	 * @param tableName table (OGR layer) name, needed for multi-layer datasets. If null, takes the first layer from dataset
	 * @param maxElements max number of visible objects
	 * @param pointStyleSet styleset for point objects
	 * @param lineStyleSet styleset for line objects
	 * @param polygonStyleSet styleset for polygon objects
	 * @param labelStyle 
	 * @throws IOException file not found or other problem opening OGR datasource
	 */
	public OgrLayer(Projection proj, String fileName, String tableName, 
			int maxElements, StyleSet<PointStyle> pointStyleSet, StyleSet<LineStyle> lineStyleSet, StyleSet<PolygonStyle> polygonStyleSet, LabelStyle labelStyle) throws IOException {
		super(proj);
		
		this.ogrHelper = new OgrHelper(fileName, tableName, proj, this, pointStyleSet, lineStyleSet, polygonStyleSet, labelStyle, maxElements, false);
		
		// ogr stdout redirect
//		new Thread() {
//		    public void run() {
//		        ogr.nativePipeSTDERRToLogcat();
//		    }
//		}.start();
		
		
		if (pointStyleSet != null) {
            minZoom = Math.min(minZoom, pointStyleSet.getFirstNonNullZoomStyleZoom());
        }
        if (lineStyleSet != null) {
            minZoom = Math.min(minZoom, lineStyleSet.getFirstNonNullZoomStyleZoom());
        }
        if (polygonStyleSet != null) {
            minZoom = Math.min(minZoom, polygonStyleSet.getFirstNonNullZoomStyleZoom());
        }
        Log.debug("ogrLayer style minZoom = "+minZoom);
        
 
	}

    @Override
	public void calculateVisibleElements(Envelope envelope, int zoom) {
	    if (this.ogrHelper == null || zoom < minZoom) {
			return;
		}
	    executeVisibilityCalculationTask(new LoadOgrDataTask(envelope,zoom));
	}
    
    @Override
    public Envelope getDataExtent() {
        return ogrHelper.getDataExtent();
    }

    public String[] getTableList() {
        return ogrHelper.getTableList();
    }

    public void setTable(String selectedTable) {
        ogrHelper.setTable(selectedTable);
        
    }

}
