/*
 *  Copyright (C) 2011 Axel Morgner, structr <structr@structr.org>
 * 
 *  This file is part of structr <http://structr.org>.
 * 
 *  structr is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  structr is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.core.entity.geo;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.geotools.data.DataUtilities;
import org.geotools.data.Query;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.DefaultMapContext;
import org.geotools.map.MapContext;
import org.geotools.map.MapLayer;
import org.geotools.styling.FeatureTypeStyle;
import org.geotools.styling.Rule;
import org.geotools.styling.Style;
import org.geotools.styling.StyleBuilder;
import org.geotools.styling.Symbolizer;
import org.neo4j.gis.spatial.geotools.data.Neo4jSpatialDataStore;
import org.neo4j.graphdb.GraphDatabaseService;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.structr.core.entity.AbstractNode;
import org.structr.common.MapHelper;
import org.structr.common.CurrentRequest;
import org.structr.core.Command;
import org.structr.core.Services;
import org.structr.core.entity.SuperUser;
import org.structr.core.node.GraphDatabaseCommand;
import org.structr.core.node.search.Search;
import org.structr.core.node.search.SearchNodeCommand;

/**
 *
 * @author axel
 */
public class Map extends AbstractNode {

    @Override
    public void renderView(StringBuilder out, final AbstractNode startNode,
            final String editUrl, final Long editNodeId) {

        if (editNodeId != null && getId() == editNodeId.longValue()) {
            renderEditFrame(out, editUrl);
        } else {

            if (isVisible()) {

                if (getDontCache() == Boolean.TRUE) {
                    renderSVGMap(out);
                    return;
                }

                String cachedSVGMap = getSvgContent();

                if (StringUtils.isBlank(cachedSVGMap)) {

                    StringBuilder cache = new StringBuilder();
                    renderSVGMap(cache);
                    setSvgContent(cache.toString());
                    out.append(cache);

                } else {
                    out.append(cachedSVGMap);
                }
            }
        }
    }

    /**
     * Render SVG content directly to output stream
     */
    @Override
    public void renderDirect(OutputStream out, final AbstractNode startNode,
            final String editUrl, final Long editNodeId) {

        try {
            if (isVisible()) {
                StringBuilder svgString = new StringBuilder();
                renderSVGMap(svgString);
                out.write(svgString.toString().getBytes());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not write SVG content to output stream: {0}", e.getStackTrace());
        }
    }

    private void renderSVGMap(StringBuilder out) {

        Command graphDbCommand = Services.command(GraphDatabaseCommand.class);
        GraphDatabaseService graphDb = (GraphDatabaseService) graphDbCommand.execute();

        MapContext mapContext = null;
        try {

            long t0 = System.currentTimeMillis();

            String featureName = null;

            String staticFeatureName = getStaticFeatureName();

            if (StringUtils.isNotBlank(staticFeatureName)) {
                featureName = staticFeatureName;
            } else {

                HttpServletRequest request = CurrentRequest.getRequest();

                String featureNameParamName = getFeatureNameParamName();
                if (featureNameParamName == null) {
                    featureNameParamName = defaultFeatureParamName;
                }

                // get the feature name from the request
                if (request != null) {
                    featureName = request.getParameter(featureNameParamName);
                }

            }

            int cx = getCanvasX();
            int cy = getCanvasY();

            boolean auto = getAutoEnvelope();

            List<MapLayer> layers = new LinkedList<MapLayer>();
            MapLayer layer = null;
            ReferencedEnvelope envelope = null;

            String shapeFilePath = getShapeFile();
            if (shapeFilePath != null) {

                // open data store from shapefile
                File shapeFile = new File(shapeFilePath);
                ShapefileDataStore dataStore = new ShapefileDataStore(shapeFile.toURI().toURL());

                // build map layer with style
                StyleBuilder sb = new StyleBuilder();
                Symbolizer sym = sb.createLineSymbolizer(Color.decode(getLineColor()), getLineWidth());
                layer = new MapLayer(dataStore.getFeatureSource(), sb.createStyle(sym));
                layers.add(layer);

            }

            // open data store from neo4j database
            Neo4jSpatialDataStore n4jstore = new Neo4jSpatialDataStore(graphDb);

            String layerName = getLayer();
            if (StringUtils.isEmpty(layerName)) {
                logger.log(Level.SEVERE, "No layer name!");
            }

            SimpleFeatureSource featureSource = n4jstore.getFeatureSource(layerName);

            GeoObject featureNode = null;

            if (auto) {

                if (featureName == null) {

                    // if no feature name is given, show all features of layer
                    envelope = featureSource.getBounds();

                } else {

                    // first, find the feature which corresponds with the requested feature
                    // (or the name of the node, if the request value is empty)
                    List<Filter> filterList = new LinkedList<Filter>();
//                    filterList.add(CQL.toFilter("NAME like '" + StringEscapeUtils.escapeSql(featureName) + "'"));
                    filterList.add(CQL.toFilter("NAME = '" + StringEscapeUtils.escapeSql(featureName) + "'"));
                    Filter filter = MapHelper.featureFactory.or(filterList);
                    Query query = new Query(layerName, filter);

                    SimpleFeatureCollection featureCollection = featureSource.getFeatures(query);

                    if (featureCollection != null && !(featureCollection.isEmpty())) {
                        SimpleFeature requestedFeature = featureCollection.features().next();
                        envelope = (ReferencedEnvelope) requestedFeature.getBounds();
                    }

                    List<AbstractNode> result = (List<AbstractNode>) Services.command(SearchNodeCommand.class).execute(new SuperUser(), null, false, false, Search.andExactName(featureName));
                    for (AbstractNode n : result) {
                        if (n instanceof GeoObject && n.isNotDeleted()) {
                            featureNode = (GeoObject) n;
                        }
                    }

                }


            } else {

                Double eminx = getEnvelopeMinX();
                Double emaxx = getEnvelopeMaxX();
                Double eminy = getEnvelopeMinY();
                Double emaxy = getEnvelopeMaxY();

                if (eminx != null && emaxx != null && eminy != null && emaxy != null) {

                    envelope = new ReferencedEnvelope(eminx, emaxx, eminy, emaxy, null);

                } else {
                    logger.log(Level.WARNING, "Manual envelope parameter incomplete");
                }
            }

            // Expand envelope as needed
            MapHelper.expandEnvelope(envelope, new Double(cx), new Double(cy));

            // search all features within this bounding
            SimpleFeatureCollection features = MapHelper.getIntersectingFeatures(graphDb, envelope, layerName);
//
//            SimpleFeatureIterator it = features.features();
//            while (it.hasNext()) {
//
//                SimpleFeature feature = it.next();
//
//                if (!(feature.getAttribute("NAME").equals(featureName))) {
//                    feature.setAttribute("NAME", "dummy");
//                }
//
//            }

            // create a style for displaying the polygons
            Symbolizer polygonSymbolizer = MapHelper.createPolygonSymbolizer(getLineColor(), getLineWidth(), getLineOpacity(), getFillColor(), getFillOpacity());
            Symbolizer textSymbolizer = MapHelper.createTextSymbolizer(getFontName(), getFontSize(), getFontColor(), getFontOpacity(), getAnchorX(), getAnchorY(), getDisplacementX(), getDisplacementY());


            Rule rule = MapHelper.styleFactory.createRule();
            rule.symbolizers().add(polygonSymbolizer);
            rule.symbolizers().add(textSymbolizer);
            FeatureTypeStyle fts = MapHelper.styleFactory.createFeatureTypeStyle(new Rule[]{rule});
            Style style = MapHelper.styleFactory.createStyle();
            style.featureTypeStyles().add(fts);

            // add features and style as a map layer to the list of map layers
            layers.add(new MapLayer(features, style));

            boolean displayCities = (getDisplayCities() == Boolean.TRUE);

            if (featureNode != null && "Country".equals(featureNode.getType()) && displayCities) {

                List<AbstractNode> subNodes = featureNode.getSortedLinkedNodes();

                List<GeoObject> geoObjects = new LinkedList<GeoObject>();
                
                List<GeoObject> cities = new LinkedList<GeoObject>();
                List<GeoObject> hotels = new LinkedList<GeoObject>();
                List<GeoObject> islands = new LinkedList<GeoObject>();
//                List<AbstractNode> result = (List<AbstractNode>) Services.command(SearchNodeCommand.class).execute(user, featureNode, false, false, Search.andExactType("City"));

                for (AbstractNode node : subNodes) {
                    
                    if ("City".equals(node.getType())) {
                        cities.add((GeoObject) node);
                    }

                    if ("Hotel".equals(node.getType())) {
                        hotels.add((GeoObject) node);
                    }

                    if ("Island".equals(node.getType())) {
                        islands.add((GeoObject) node);
                    }

                }
                
                // no cities -> show hotels directly
                if (cities.isEmpty()) {
                    geoObjects.addAll(hotels);
                } else {
                    geoObjects.addAll(cities);
                }
                
                SimpleFeatureCollection collection = MapHelper.createPointsFromGeoObjects(geoObjects);

                Symbolizer cityTextSym = MapHelper.createTextSymbolizer(getPointFontName(), getPointFontSize(), getPointFontColor(), getPointFontOpacity(), getLabelAnchorX(), getLabelAnchorY(), getLabelDisplacementX(), getLabelDisplacementY());
                Symbolizer cityPointSym = MapHelper.createPointSymbolizer(getPointShape(), getPointDiameter(), getPointStrokeColor(), getPointStrokeLineWidth(), getPointFillColor(), this.getPointFillOpacity());
//            Symbolizer cityPolygonSymbolizer = MapHelper.createPolygonSymbolizer("#000000", 1, 1, "#000000", 1);

                Rule rule2 = MapHelper.styleFactory.createRule();
                rule2.symbolizers().add(cityTextSym);
//            rule2.symbolizers().add(cityPolygonSymbolizer);
                rule2.symbolizers().add(cityPointSym);

                FeatureTypeStyle fts2 = MapHelper.styleFactory.createFeatureTypeStyle(new Rule[]{rule2});
                Style style2 = MapHelper.styleFactory.createStyle();
                style2.featureTypeStyles().add(fts2);

                SimpleFeatureSource source = DataUtilities.source(collection);
                SimpleFeatureCollection subFeatures = source.getFeatures();

                //Style pointStyle = SLD.createPointStyle("Square", Color.yellow, Color.yellow, 1, 3);
                //pointStyle.featureTypeStyles().add(fts2);

                if (!subFeatures.isEmpty()) {

                    // add features and style as a map layer to the list of map layers
                    layers.add(new MapLayer(subFeatures, style2));

                }

            }

            // create a map context
            mapContext = new DefaultMapContext(layers.toArray(new MapLayer[]{}));

            // render map to SVG
            MapHelper.renderSVGDocument(out, mapContext, envelope, cx, cy, getOptimizeFtsRendering(), getLineWidthOptimization());

            // clear map content
            mapContext.dispose();

            long t1 = System.currentTimeMillis();

            logger.log(Level.INFO, "SVG image successfully rendered in {0} ms", (t1 - t0));


        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Error while rendering map to SVG", t);
        } finally {
            if (mapContext != null) {
                mapContext.dispose();
            }
        }
    }

    @Override
    public String getIconSrc() {
        return ICON_SRC;
    }

    @Override
    public void onNodeCreation() {
    }

    @Override
    public void onNodeInstantiation() {
    }

// <editor-fold defaultstate="collapsed" desc="getter and setter methods">
// getter and setter methods
//    public String getContentType() {
//        return (String) getProperty(CONTENT_TYPE_KEY);
//    }

    public int getCanvasX() {
        return getIntProperty(CANVAS_X_KEY);
    }

    public int getCanvasY() {
        return getIntProperty(CANVAS_Y_KEY);
    }

    public double getEnvelopeMinX() {
        return getDoubleProperty(ENVELOPE_MIN_X_KEY);
    }

    public double getEnvelopeMinY() {
        return getDoubleProperty(ENVELOPE_MIN_Y_KEY);
    }

    public double getEnvelopeMaxX() {
        return getDoubleProperty(ENVELOPE_MAX_X_KEY);
    }

    public double getEnvelopeMaxY() {
        return getDoubleProperty(ENVELOPE_MAX_Y_KEY);
    }

    public String getShapeFile() {
        return (String) getProperty(SHAPEFILE_KEY);
    }

    public String getLayer() {
        return (String) getProperty(LAYER_KEY);
    }

    public int getLineWidth() {
        return getIntProperty(LINE_WIDTH_KEY);
    }

    public String getLineColor() {
        return (String) getProperty(LINE_COLOR_KEY);
    }

    public double getLineOpacity() {
        return getDoubleProperty(LINE_OPACITY_KEY);
    }

    public String getFillColor() {
        return (String) getProperty(FILL_COLOR_KEY);
    }

    public double getFillOpacity() {
        return getDoubleProperty(FILL_OPACITY_KEY);
    }

    public boolean getOptimizeFtsRendering() {
        return getBooleanProperty(OPTIMIZE_FTS_RENDERING_KEY);
    }

    public boolean getLineWidthOptimization() {
        return getBooleanProperty(LINE_WIDTH_OPTIMIZATION_KEY);
    }

    public boolean getAutoEnvelope() {
        return getBooleanProperty(AUTO_ENVELOPE_KEY);
    }

    public boolean getStatic() {
        return getBooleanProperty(STATIC_KEY);
    }

    public boolean getDontCache() {
        return getBooleanProperty(DONT_CACHE_KEY);
    }

    public boolean getDisplayCities() {
        return getBooleanProperty(DISPLAY_CITIES_KEY);
    }

    public String getFontName() {
        return (String) getProperty(FONT_NAME_KEY);
    }

    public double getFontSize() {
        return getDoubleProperty(FONT_SIZE_KEY);
    }

    public String getFontColor() {
        return (String) getProperty(FONT_COLOR_KEY);
    }

    public double getFontOpacity() {
        return getDoubleProperty(FONT_OPACITY_KEY);
    }

    public double getAnchorX() {
        return getDoubleProperty(LABEL_ANCHOR_X_KEY);
    }

    public double getAnchorY() {
        return getDoubleProperty(LABEL_ANCHOR_Y_KEY);
    }

    public double getDisplacementX() {
        return getDoubleProperty(LABEL_DISPLACEMENT_X_KEY);
    }

    public double getDisplacementY() {
        return getDoubleProperty(LABEL_DISPLACEMENT_X_KEY);
    }

    public String getStaticFeatureName() {
        return (String) getProperty(STATIC_FEATURE_NAME_KEY);
    }

    public String getFeatureNameParamName() {
        return (String) getProperty(FEATURE_NAME_PARAM_NAME_KEY);
    }

    public String getSvgContent() {
        return (String) getProperty(SVG_CONTENT_KEY);
    }

    public double getLabelAnchorX() {
        return getDoubleProperty(LABEL_ANCHOR_X_KEY);
    }

    public double getLabelAnchorY() {
        return getDoubleProperty(LABEL_ANCHOR_Y_KEY);
    }

    public double getLabelDisplacementX() {
        return getDoubleProperty(LABEL_DISPLACEMENT_X_KEY);
    }

    public double getLabelDisplacementY() {
        return getDoubleProperty(LABEL_DISPLACEMENT_Y_KEY);
    }

    public String getPointShape() {
        return getStringProperty(POINT_SHAPE_KEY);
    }

    public int getPointDiameter() {
        return getIntProperty(POINT_DIAMETER_KEY);
    }

    public String getPointStrokeColor() {
        return getStringProperty(POINT_STROKE_COLOR_KEY);
    }

    public int getPointStrokeLineWidth() {
        return getIntProperty(POINT_STROKE_LINE_WIDTH_KEY);
    }

    public String getPointFillColor() {
        return getStringProperty(POINT_FILL_COLOR_KEY);
    }

    public double getPointFillOpacity() {
        return getDoubleProperty(POINT_FILL_OPACITY_KEY);
    }

    public String getPointFontName() {
        return getStringProperty(POINT_FONT_NAME_KEY);
    }

    public double getPointFontSize() {
        return getDoubleProperty(POINT_FONT_SIZE_KEY);
    }

    public String getPointFontColor() {
        return getStringProperty(POINT_FONT_COLOR_KEY);
    }

    public double getPointFontOpacity() {
        return getDoubleProperty(POINT_FONT_OPACITY_KEY);
    }

    //########################################
    public void setContentType(final String contentType) {
        setProperty(CONTENT_TYPE_KEY, contentType);
    }

    public void setCanvasX(final int value) {
        setProperty(CANVAS_X_KEY, value);
    }

    public void setCanvasY(final int value) {
        setProperty(CANVAS_Y_KEY, value);
    }

    public void setEnvelopeMinX(final double value) {
        setProperty(ENVELOPE_MIN_X_KEY, value);
    }

    public void setEnvelopeMinY(final double value) {
        setProperty(ENVELOPE_MIN_Y_KEY, value);
    }

    public void setEnvelopeMaxX(final double value) {
        setProperty(ENVELOPE_MAX_X_KEY, value);
    }

    public void setEnvelopeMaxY(final double value) {
        setProperty(ENVELOPE_MAX_Y_KEY, value);
    }

    public void setShapeFile(final String value) {
        setProperty(SHAPEFILE_KEY, value);
    }

    public void setLayer(final String value) {
        setProperty(LAYER_KEY, value);
    }

    public void setLineWidth(final int value) {
        setProperty(LINE_WIDTH_KEY, value);
    }

    public void setLineColor(final String value) {
        setProperty(LINE_COLOR_KEY, value);
    }

    public void setLineOpacity(final double value) {
        setProperty(LINE_OPACITY_KEY, value);
    }

    public void setFillColor(final String value) {
        setProperty(FILL_COLOR_KEY, value);
    }

    public void setFillOpacity(final double value) {
        setProperty(FILL_OPACITY_KEY, value);
    }

    public void setOptimizeFtsRendering(final boolean value) {
        setProperty(OPTIMIZE_FTS_RENDERING_KEY, value);
    }

    public void setLineWidthOptimization(final boolean value) {
        setProperty(LINE_WIDTH_OPTIMIZATION_KEY, value);
    }

    public void setAutoEnvelope(final boolean value) {
        setProperty(AUTO_ENVELOPE_KEY, value);
    }

    public void setStatic(final boolean value) {
        setProperty(STATIC_KEY, value);
    }

    public void setDontCache(final boolean value) {
        setProperty(DONT_CACHE_KEY, value);
    }

    public void setDisplayCities(final boolean value) {
        setProperty(DISPLAY_CITIES_KEY, value);
    }

    public void setFontName(final String value) {
        setProperty(FONT_NAME_KEY, value);
    }

    public void setFontSize(final double value) {
        setProperty(FONT_SIZE_KEY, value);
    }

    public void setFontColor(final String value) {
        setProperty(FONT_COLOR_KEY, value);
    }

    public void setFontOpacity(final double value) {
        setProperty(FONT_OPACITY_KEY, value);
    }

    public void setStaticFeatureName(final String value) {
        setProperty(STATIC_FEATURE_NAME_KEY, value);
    }

    public void setFeatureNameParamName(final String value) {
        setProperty(FEATURE_NAME_PARAM_NAME_KEY, value);
    }

    public void setSvgContent(final String svgContent) {
        setProperty(SVG_CONTENT_KEY, svgContent);
    }

    public void setLabelAnchorX(final double value) {
        setProperty(LABEL_ANCHOR_X_KEY, value);
    }

    public void setLabelAnchorY(final double value) {
        setProperty(LABEL_ANCHOR_Y_KEY, value);
    }

    public void setLabelDisplacementX(final double value) {
        setProperty(LABEL_DISPLACEMENT_X_KEY, value);
    }

    public void setLabelDisplacementY(final double value) {
        setProperty(LABEL_DISPLACEMENT_Y_KEY, value);
    }

    public void setPointShape(final String value) {
        setProperty(POINT_SHAPE_KEY, value);
    }

    public void setPointDiameter(final int value) {
        setProperty(POINT_DIAMETER_KEY, value);
    }

    public void setPointStrokeColor(final String value) {
        setProperty(POINT_STROKE_COLOR_KEY, value);
    }

    public void setPointStrokeLineWidth(final int value) {
        setProperty(POINT_STROKE_LINE_WIDTH_KEY, value);
    }

    public void setPointFillColor(final String value) {
        setProperty(POINT_FILL_COLOR_KEY, value);
    }

    public void setPointFillOpacity(final double value) {
        setProperty(POINT_FILL_OPACITY_KEY, value);
    }

    public void setPointFontName(final String value) {
        setProperty(POINT_FONT_NAME_KEY, value);
    }

    public void setPointFontSize(final double value) {
        setProperty(POINT_FONT_SIZE_KEY, value);
    }

    public void setPointFontColor(final String value) {
        setProperty(POINT_FONT_COLOR_KEY, value);
    }

    public void setPointFontOpacity(final double value) {
        setProperty(POINT_FONT_OPACITY_KEY, value);
    }
    // </editor-fold>
    // Attributes
// <editor-fold defaultstate="collapsed" desc="Attributes">
    private final static String ICON_SRC = "/images/map.png";
    private static final Logger logger = Logger.getLogger(Map.class.getName());
    private final static String defaultFeatureParamName = "name";
    public static final String SVG_CONTENT_KEY = "svgContent";
    public final static String CONTENT_TYPE_KEY = "contentType";
    public static final String ENVELOPE_MIN_X_KEY = "envelopeMinX";
    public static final String ENVELOPE_MAX_X_KEY = "envelopeMaxX";
    public static final String ENVELOPE_MIN_Y_KEY = "envelopeMinY";
    public static final String ENVELOPE_MAX_Y_KEY = "envelopeMaxY";
    public static final String CANVAS_X_KEY = "canvasX";
    public static final String CANVAS_Y_KEY = "canvasY";
    public static final String LINE_COLOR_KEY = "lineColor";
    public static final String LINE_WIDTH_KEY = "lineWidth";
    public static final String LINE_OPACITY_KEY = "lineOpacity";
    public static final String FILL_COLOR_KEY = "fillColor";
    public static final String FILL_OPACITY_KEY = "fillOpacity";
    public static final String FONT_NAME_KEY = "fontName";
    public static final String FONT_SIZE_KEY = "fontSize";
    public static final String FONT_COLOR_KEY = "fontColor";
    public static final String FONT_OPACITY_KEY = "fontOpacity";
    public static final String LABEL_ANCHOR_X_KEY = "labelAnchorX";
    public static final String LABEL_ANCHOR_Y_KEY = "labelAnchorY";
    public static final String LABEL_DISPLACEMENT_X_KEY = "labelDisplacementX";
    public static final String LABEL_DISPLACEMENT_Y_KEY = "labelDisplacementY";
    public static final String SHAPEFILE_KEY = "shapeFile";
    public static final String POINT_SHAPE_KEY = "pointShape";
    public static final String POINT_DIAMETER_KEY = "pointDiameter";
    public static final String POINT_STROKE_COLOR_KEY = "pointStrokeColor";
    public static final String POINT_STROKE_LINE_WIDTH_KEY = "pointStrokeLineWidth";
    public static final String POINT_FILL_COLOR_KEY = "pointFillColor";
    public static final String POINT_FILL_OPACITY_KEY = "pointFillOpacity";
    public static final String POINT_FONT_NAME_KEY = "pointFontName";
    public static final String POINT_FONT_SIZE_KEY = "pointFontSize";
    public static final String POINT_FONT_COLOR_KEY = "pointFontColor";
    public static final String POINT_FONT_OPACITY_KEY = "pointFontOpacity";
    public static final String LAYER_KEY = "layer";
    public static final String OPTIMIZE_FTS_RENDERING_KEY = "optimizeFtsRendering";
    public static final String LINE_WIDTH_OPTIMIZATION_KEY = "lineWidthOptimization";
    public static final String AUTO_ENVELOPE_KEY = "autoEnvelope";
    public static final String FEATURE_NAME_PARAM_NAME_KEY = "featureNameParamName";
    public static final String STATIC_FEATURE_NAME_KEY = "staticFeatureName";
    public static final String STATIC_KEY = "static"; // Don't take request parameters into account
    public static final String DONT_CACHE_KEY = "dontCache";
    public static final String DISPLAY_CITIES_KEY = "displayCities";
    // </editor-fold>
}
