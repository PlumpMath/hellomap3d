package com.nutiteq.layers.vector;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.gdal.ogr.DataSource;
import org.gdal.ogr.Feature;
import org.gdal.ogr.FeatureDefn;
import org.gdal.ogr.FieldDefn;
import org.gdal.ogr.Geometry;
import org.gdal.ogr.Layer;
import org.gdal.ogr.ogr;
import org.gdal.ogr.ogrConstants;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import org.gdal.ogr.ogr;

import com.nutiteq.components.Envelope;
import com.nutiteq.components.MapPos;
import com.nutiteq.geometry.Line;
import com.nutiteq.geometry.Point;
import com.nutiteq.geometry.Polygon;
import com.nutiteq.log.Log;
import com.nutiteq.projections.Projection;
import com.nutiteq.style.LabelStyle;
import com.nutiteq.style.LineStyle;
import com.nutiteq.style.PointStyle;
import com.nutiteq.style.PolygonStyle;
import com.nutiteq.style.StyleSet;
import com.nutiteq.ui.DefaultLabel;
import com.nutiteq.ui.Label;
import com.nutiteq.utils.WkbRead;
import com.nutiteq.utils.WktWriter;
import com.nutiteq.vectorlayers.VectorLayer;

/**
 * Helper class for OGR data reading and writing
 * 
 * @author jaak
 *
 */
public class OgrHelper {

    public static final String TABLE_SEPARATOR = " |";
    private Layer layer;
    private boolean transformNeeded;
    private Projection projection;
    
    private DataSource hDataset;
    private String[] fieldNames;
    private CoordinateTransformation transformerToMap;
    private VectorLayer vectorLayer;
    private CoordinateTransformation transformerToData;
    private StyleSet<PointStyle> pointStyleSet;
    private StyleSet<LineStyle> lineStyleSet;
    private StyleSet<PolygonStyle> polygonStyleSet;
    private int maxElements;
    private LabelStyle labelStyle;
    private static Vector<String> knownExtensions = new Vector<String>();
    
    private static final String EPSG_3785_WKT = "PROJCS[\"Google Maps Global Mercator\",    GEOGCS[\"WGS 84\",        DATUM[\"WGS_1984\",            SPHEROID[\"WGS 84\",6378137,298.257223563,                AUTHORITY[\"EPSG\",\"7030\"]],            AUTHORITY[\"EPSG\",\"6326\"]],        PRIMEM[\"Greenwich\",0,            AUTHORITY[\"EPSG\",\"8901\"]],        UNIT[\"degree\",0.01745329251994328,            AUTHORITY[\"EPSG\",\"9122\"]],        AUTHORITY[\"EPSG\",\"4326\"]],    PROJECTION[\"Mercator_2SP\"],    PARAMETER[\"standard_parallel_1\",0],    PARAMETER[\"latitude_of_origin\",0],    PARAMETER[\"central_meridian\",0],    PARAMETER[\"false_easting\",0],    PARAMETER[\"false_northing\",0],    UNIT[\"Meter\",1],    EXTENSION[\"PROJ4\",\"+proj=merc +a=6378137 +b=6378137 +lat_ts=0.0 +lon_0=0.0 +x_0=0.0 +y_0=0 +k=1.0 +units=m +nadgrids=@null +wktext  +no_defs\"],    AUTHORITY[\"EPSG\",\"3785\"]]";

    // define some synonyms of EPSG3785 to avoid transformations for these
    private static final String EPSG_3785_PROJ4 =       "+proj=merc +a=6378137 +b=6378137 +lat_ts=0.0 +lon_0=0.0 +x_0=0.0 +y_0=0 +k=1.0 +units=m +nadgrids=@null +no_defs";
    private static final String EPSG_3785_PROJ4BIS =    "+proj=merc +lon_0=0 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs"; 
    private static final String EPSG_3785_PROJ4BIS2 =   "+proj=merc +lon_0=0 +k=1 +x_0=0 +y_0=0 +a=6378137 +b=6378137 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs";
    private static final String EPSG_3785_PROJ4BIS3 =   "+proj=merc +lon_0=0 +k=1 +x_0=0 +y_0=0 +ellps=WGS84 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs";


    public OgrHelper(String fileName, String tableName, Projection proj,
            VectorLayer vectorLayer, StyleSet<PointStyle> pointStyleSet,
            StyleSet<LineStyle> lineStyleSet,
            StyleSet<PolygonStyle> polygonStyleSet, LabelStyle labelStyle, int maxElements, boolean update) throws IOException {
        this.projection = proj;
        this.vectorLayer = vectorLayer;
        this.pointStyleSet = pointStyleSet;
        this.lineStyleSet = lineStyleSet;
        this.polygonStyleSet = polygonStyleSet;
        this.labelStyle = labelStyle;
        
        this.maxElements = maxElements;
        
        // open dataset
        hDataset = ogr.Open(fileName, update);
        if (hDataset == null) {
            Log.error("OgrLayer: unable to open dataset '"+fileName+"'");
            
            printSupportedDrivers();    
            throw new IOException("OgrLayer: unable to open dataset '"+fileName+"'");
        }
        
        // debug print all layer data 
        for (int i=0; i < hDataset.GetLayerCount(); i++){
            Layer layerI = hDataset.GetLayer(i);
            Log.debug("---");
            printLayerDetails(layerI);
        }
        
        // open the layer, just pick the first one if name not given
        if (tableName == null) {
            layer = hDataset.GetLayer(0);
            tableName = layer.GetName();
        }
        else {
            // you can use SQL here
          //layer = hDataset.ExecuteSQL("SELECT * FROM " + tableName+ " WHERE OKOOD=0616");
            layer = hDataset.GetLayerByName(tableName);
        }
        if (layer == null) {
            Log.error("OgrLayer: could not find layer '"+tableName+"'");
            return;
        }
        
        Log.info("opening layer: "+layer.GetName());
        
        this.fieldNames = getFieldNames(layer);
        
        initProjections();
    }

    public List<com.nutiteq.geometry.Geometry> loadData(Envelope envelope, int zoom) {
        long timeStart = System.currentTimeMillis();

        MapPos minPosData = projection.fromInternal(envelope.getMinX(), envelope.getMinY());
        MapPos maxPosData = projection.fromInternal(envelope.getMaxX(), envelope.getMaxY());
        
        MapPos minPos;
        MapPos maxPos;
        if(!transformNeeded){
            minPos = minPosData;
            maxPos = maxPosData;
            
        }else{
            // conversion needed from layer projection to data projection, to apply the filter
            minPos = transformPoint(minPosData, transformerToData);
            maxPos = transformPoint(maxPosData, transformerToData);
        }

        Log.debug("filter: "+minPos+" - "+maxPos);
        
        layer.SetSpatialFilterRect(
                Math.min(minPos.x, maxPos.x), Math.min(minPos.y, maxPos.y),
                Math.max(minPos.x, maxPos.x), Math.max(minPos.y, maxPos.y)
            );

        List<com.nutiteq.geometry.Geometry> newVisibleElementsList = new LinkedList<com.nutiteq.geometry.Geometry>();

        layer.ResetReading();
        Feature feature = layer.GetNextFeature();
        Geometry poSrcGeom;
        
        for (int n = 0; feature != null && n < maxElements; n++) {

            poSrcGeom = feature.GetGeometryRef();
            int eType = poSrcGeom.GetGeometryType();
            if (eType == ogr.wkbUnknown) {
                Log.error("unknown object type "+eType);
                continue;
            }

            final Map<String, String> userData = new HashMap<String, String>();

            userData.put("FID",String.valueOf(feature.GetFID()));
            
            for(int field=0; field<feature.GetFieldCount();field++){
                userData.put(this.fieldNames[field],
                        feature.GetFieldAsString(field));
            }
            
            Label label = createLabel(userData);

            byte[] geomWkb = poSrcGeom.ExportToWkb();
            com.nutiteq.geometry.Geometry[] geoms = WkbRead.readWkb(new ByteArrayInputStream(geomWkb), userData);
            
            // add stylesets, new objects are needed for this
            for(int i = 0; i<geoms.length; i++){
                com.nutiteq.geometry.Geometry object = geoms[i];           
                com.nutiteq.geometry.Geometry newObject = null;
                
                if(transformNeeded){
                    if(object instanceof com.nutiteq.geometry.Point){
                        newObject = new com.nutiteq.geometry.Point((transformPoint(((Point) object).getMapPos(), transformerToMap)), label, pointStyleSet, object.userData);
                    }else if(object instanceof Line){
                        newObject = new com.nutiteq.geometry.Line(transformPointList(((Line) object).getVertexList(), transformerToMap), label, lineStyleSet, object.userData);
                    }else if(object instanceof Polygon){
                        newObject = new com.nutiteq.geometry.Polygon(transformPointList(((Polygon) object).getVertexList(), transformerToMap), transformPointListList(((Polygon) object).getHolePolygonList(), transformerToMap), label, polygonStyleSet, object.userData);
                    }
                    
                }else{
                    if(object instanceof com.nutiteq.geometry.Point){
                        newObject = new com.nutiteq.geometry.Point((((Point) object).getMapPos()), label, pointStyleSet, object.userData);
                    }else if(object instanceof Line){
                        newObject = new com.nutiteq.geometry.Line(((Line) object).getVertexList(), label, lineStyleSet, object.userData);
                    }else if(object instanceof Polygon){
                        newObject = new com.nutiteq.geometry.Polygon(((Polygon) object).getVertexList(), ((Polygon) object).getHolePolygonList(), label, polygonStyleSet, object.userData);
                    }
                    
                }
                if(newObject != null){
                    newObject.attachToLayer(vectorLayer);
                    newObject.setActiveStyle(zoom);
                    newVisibleElementsList.add(newObject);
                }else{
                    Log.warning("Loaded geometry is null, probably invalid data");
                }
                
            }

            feature = layer.GetNextFeature();
        }
        long timeEnd = System.currentTimeMillis();
        Log.debug("OgrLayer loaded "+layer.GetName()+" N:"+ newVisibleElementsList.size()+" time ms:"+(timeEnd-timeStart));
        return newVisibleElementsList;
        
    }
    
    public long insertElement(com.nutiteq.geometry.Geometry element) {
        
        String wktGeom = WktWriter.writeWkt(element, getGeometryType(element));
        
        Feature feature = new Feature(layer.GetLayerDefn());
        feature.SetGeometryDirectly(Geometry.CreateFromWkt(wktGeom));
        HashMap<String, String> fields = (HashMap<String, String>) element.userData;
        Set<String> fieldsI = fields.keySet();
        for(String field : fieldsI){
           feature.SetField(field, fields.get(field));   
        }
        if(layer.CreateFeature(feature) != ogrConstants.OGRERR_NONE){
            Log.error("OgrLayer: could not create feature");
        }
        
        long id = feature.GetFID();
        layer.SyncToDisk();
        
        return id;
    }
    
    public void updateElement(long id, com.nutiteq.geometry.Geometry element) {

        String wktGeom = WktWriter.writeWkt(element, getGeometryType(element));

        Feature feature = new Feature(layer.GetLayerDefn());
        feature.SetFID((int) id);
        
        HashMap<String, String> fields = (HashMap<String, String>) element.userData;

        Set<String> fieldsI = fields.keySet();
        for(String field : fieldsI){
           feature.SetField(field, fields.get(field));   
        }
        feature.SetGeometryDirectly(Geometry.CreateFromWkt(wktGeom));
        if(layer.SetFeature(feature) != ogrConstants.OGRERR_NONE){
            Log.error("OgrLayer: could not update feature");
        }
        
        layer.SyncToDisk();
    }

    public void deleteElement(long id) {
        if(layer.DeleteFeature((int) id) != ogrConstants.OGRERR_NONE){
            Log.error("OgrLayer: could not delete feature with id "+id);
        }
        
        layer.SyncToDisk();
    }
    
    
    private List<List<MapPos>> transformPointListList(
            List<List<MapPos>> posList,
            CoordinateTransformation transformer) {
        if(posList == null)
            return null;
        List<List<MapPos>> res = new ArrayList<List<MapPos>>();
        for(List<MapPos> pointList: posList){
            res.add(transformPointList(pointList,transformer));
        }
        return res;
    }


    private List<MapPos> transformPointList(List<MapPos> vertexList,
            CoordinateTransformation transformer) {
        if(vertexList == null)
            return null;
        List<MapPos> res = new ArrayList<MapPos>();
        for(MapPos point: vertexList){
            res.add(transformPoint(point,transformer));
        }
        return res;
    }


    private MapPos transformPoint(MapPos pos, CoordinateTransformation hTransform) {
        
        double[] transPoint = new double[3];
        if (hTransform != null) {
            hTransform.TransformPoint(transPoint, pos.x, pos.y, 0);
        }
        
        return new MapPos(transPoint[0], transPoint[1]);
    }
    
    private String[] getFieldNames(Layer layer) {

        FeatureDefn poDefn = layer.GetLayerDefn();
        
        String[] names = new String[poDefn.GetFieldCount()];
        
        for (int iAttr = 0; iAttr < poDefn.GetFieldCount(); iAttr++) {
            FieldDefn poField = poDefn.GetFieldDefn(iAttr);
            names[iAttr] = poField.GetNameRef();
        }
        return names;
    }
    
    // print layer details for troubleshooting. Code from ogrinfo.java
    public void printLayerDetails(Layer layer) {

        FeatureDefn poDefn = layer.GetLayerDefn();
        Log.debug("Layer name: " + poDefn.GetName());
        Log.debug("Geometry: " + ogr.GeometryTypeToName(poDefn.GetGeomType()));

        Log.debug("Feature Count: " + layer.GetFeatureCount());

        double oExt[] = layer.GetExtent(true);
        if (oExt != null)
            Log.debug("Extent: (" + oExt[0] + ", " + oExt[2] + ") - ("
                    + oExt[1] + ", " + oExt[3] + ")");

        String pszWKT;

        if (layer.GetSpatialRef() == null)
            pszWKT = "(unknown)";
        else {
            pszWKT = layer.GetSpatialRef().ExportToPrettyWkt();
        }

        Log.debug("Layer SRS WKT:\n" + pszWKT);

        if (layer.GetFIDColumn().length() > 0)
            Log.debug("FID Column = " + layer.GetFIDColumn());

        if (layer.GetGeometryColumn().length() > 0)
            Log.debug("Geometry Column = " + layer.GetGeometryColumn());

        for (int iAttr = 0; iAttr < poDefn.GetFieldCount(); iAttr++) {
            FieldDefn poField = poDefn.GetFieldDefn(iAttr);

            Log.debug(poField.GetNameRef() + ": "
                    + poField.GetFieldTypeName(poField.GetFieldType()) + " ("
                    + poField.GetWidth() + "." + poField.GetPrecision() + ")");
        }
    }

    public void printSupportedDrivers() {
        Log.debug("Supported drivers:");
        for( int iDriver = 0; iDriver < ogr.GetDriverCount(); iDriver++ )
        {
            Log.debug( " -> " + ogr.GetDriver(iDriver).GetName() );
        }
    }

    protected Label createLabel(Map<String, String> userData) {
        StringBuffer labelTxt = new StringBuffer();
        for(Map.Entry<String, String> entry : userData.entrySet()){
            labelTxt.append(entry.getKey() + ": " + entry.getValue()+"\n");
        }
        
        return new DefaultLabel(layer.GetName(), labelTxt.toString(), labelStyle);
    }
    

    public Envelope getDataExtent() {

        double oExt[] = layer.GetExtent(true);
        if (oExt != null){
            Log.debug("Extent: (" + oExt[0] + ", " + oExt[2] + ") - ("
                    + oExt[1] + ", " + oExt[3] + ")");
            
            MapPos minPos = new MapPos(oExt[0],oExt[2]);
            MapPos maxPos = new MapPos(oExt[1],oExt[3]);
            
            if(transformNeeded){
                minPos = transformPoint(new MapPos(oExt[0],oExt[2]), transformerToMap);
                maxPos = transformPoint(new MapPos(oExt[1],oExt[3]), transformerToMap);
            }
            
//            return GeoUtils.transformBboxProj4(dataExtent, layer.GetSpatialRef().ExportToProj4(), EPSG_3785_PROJ4);
            return new Envelope(minPos.x,maxPos.x,minPos.y,maxPos.y);
        }
        
        return null;
    }


    public static boolean canOpen(File file) {

        String fileExtension = file.getName().substring(file.getName().lastIndexOf(".")+1).toLowerCase();
        
        // use cached list of known extensions
        if (knownExtensions != null && knownExtensions.contains(fileExtension)){
            return true;
        }
        
        // not found in known list, try to open
        if(ogr.GetDriverCount() == 0){
            ogr.RegisterAll();
        }
        
        if(ogr.Open(file.getAbsolutePath()) != null){
            // was able to open, lets cache extensions
            knownExtensions.add(fileExtension);
            return true;
        }

        return false;
    }
    
    
    /**
     * Creates transformers for projection transformations, if needed
     */
    private void initProjections() {
        
        SpatialReference layerProjection = new SpatialReference();
        //layerProjection.ImportFromProj4(EPSG_3785_PROJ4);
        layerProjection.ImportFromWkt(EPSG_3785_WKT);
        SpatialReference dataProj = layer.GetSpatialRef();
        
        String dataProjName  = EPSG_3785_PROJ4; // change here to use any other projection as default
        if(dataProj == null){
            Log.warning("projection of table "+layer.GetName()+" unknown, using EPSG:3785 as default. Change OgrLayer code to use anything else.");
        }else{
            dataProjName = dataProj.ExportToProj4().trim();
        }
        
        Log.debug("dataProj: "+dataProjName);
        
        transformNeeded = ! 
                (dataProjName.equals(EPSG_3785_PROJ4) 
                        || dataProjName.equals(EPSG_3785_PROJ4BIS) 
                        || dataProjName.equals(EPSG_3785_PROJ4BIS2)
                        || dataProjName.equals(EPSG_3785_PROJ4BIS3));
        Log.debug("transform needed: "+transformNeeded);
        
        transformerToData = new CoordinateTransformation(layerProjection, dataProj);
        
        transformerToMap = new CoordinateTransformation(dataProj,layerProjection);
    }

    private String getGeometryType(com.nutiteq.geometry.Geometry element) {
        if (element instanceof Point) {
            return "POINT";
        } else if (element instanceof Line) {
            return "LINE";
        } else if (element instanceof Polygon) {
            return "POLYGON";
        }
        return null;
    }

    public String[] getTableList() {
        Vector<String> tables = new Vector<String>();
        // debug print all layer data 
        for (int i=0; i < hDataset.GetLayerCount(); i++){
            Layer layerI = hDataset.GetLayer(i);
            tables.add(layerI.GetName() + TABLE_SEPARATOR + layerI.GetFeatureCount()+" "+ogr.GeometryTypeToName(layerI.GetGeomType()));
        }
        return tables.toArray(new String[tables.size()]);
    }

    public void setTable(String tableName) {
        this.layer = hDataset.GetLayerByName(tableName);
        this.fieldNames = getFieldNames(layer);
    }
    
    
}
