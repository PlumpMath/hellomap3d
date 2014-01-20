This is a demo project for **Nutiteq 3D Android mapping SDK**. Nutiteq SDK is a replacement for Google Maps API (MapView) for Android, so it has all your usual mapping SDK features (map panning, vector/raster overlays etc) plus some extras: 
* 2.5/3D view: map tilting, rotating and 3D models
* offline maps (MBTiles, persistent caching etc)
* Vector editing with online and offline backend
* Many datasources built-in: raster and vector, online and offline
* supports map projections, usable for GIS apps

![Screenshot with 3D models](https://dl.dropbox.com/u/3573333/mapxt_3d_tallinn_device-2012-07-25-124845.png)

# Usage
To get started see [project Wiki pages](https://github.com/nutiteq/hellomap3d/wiki). It shows how to create a map and add layers on top of that.

# Contribution 
We welcome any kind of contributions: questions, pull requests, questions, feedback, wiki updates etc. Please join [nutiteq-dev@googlegroups.com] (https://groups.google.com/forum/#!forum/nutiteq-dev) for discussions.

# License
There are currently following options:
* [Free OpenStreetMap License](https://github.com/nutiteq/hellomap3d/wiki/Free-openstreetmap-license) - usable with OSM base map
* Free evaluation/development license. Just use the SDK for development, no key or registration needed
* Commercial license, (c) Nutiteq Llc. Request nutiteq@nutiteq.com for details

Third party software licenses for core library:
* Poly2tri - New BSD License http://code.google.com/p/poly2tri/
* Google Protobuf library - used for 3D layers

Some additional layers use free and open source 3rd party software libraries:
* Mapsforge - used for respective layer
* OGR/GDAL and Proj.4 - used for OGR and GDAL layers
* Spatialite lib - used in Spatialite and 3DPolygon layers. 
* JavaProj - Apache License 2.0 http://sourceforge.net/projects/jproj4/ 
* JTS - used in 3D Polygon layer with OSM Roofs (Simple 3D)
 
All these libraries are free and open source.

The Hello Map 3D application code is free and licensed under MIT license terms:

Copyright (C) 2012-2013 Nutiteq Llc

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
