[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

# AR-Localization 
An Augmented Reality Android App for precise Indoor-Localization based on ARCore Cloud Anchors and the ARCore Geospatial API.

This project was developed for my master’s thesis at the HTW-Berlin with the title:  
``Entwicklung eines hybriden Indoor-Outdoor-Lokalisierungssystems auf Basis der ARCore Geospatial API und Cloud Anchor``

# App
The app is set up in 2 main parts to allow indoor localization:

1. First, an indoor environment has to be mapped out with the app, from which a floorplan is created that is available to all other users. 
ARCore Cloud Anchors are placed regularly to allow positioning within the map, while the Geospatial API is used at the start to attach a global reference in the form of coordinates to a floorplan.  
In this video, the process of mapping out an area of the TU Berlin is shown:
https://www.youtube.com/watch?v=NUa_R3DZ12U

2. By resolving the associated cloud anchors of a mapped floorplan, a user can find its precise global position with coordinates anywhere in the mapped out area. 
After initial positioning, further cloud anchors along the map are regularly resolved to recalibrate the localization.    
Additionally, a user can select a cloud anchor to start a navigation to it.  
In this video, the process of localization is shown in the same area of the TU Berlin:
https://www.youtube.com/watch?v=5aMPyOTZnhQ


# Setup & Usage
Clone this repository and import it into your Android Studio.

To run this app, [this custom SceneView branch](https://github.com/morhenny/sceneview-android/tree/0.6.1-SNAPSHOT_resolve_multiple_anchors) needs to be cloned and published locally with `publishToMavenLocal`. 

An account for the Google Cloud Console needs to be created and the `ARCore API` activated with OAuth 2.0 authentication.

An API-Key for the Maps SDK for Android is required as well, which needs to be set within the `local.properties` as `MAPS_KEY`.

A Firebase `Cloud-Firestore` database needs to set up as well and [added to the project](https://firebase.google.com/docs/android/setup?hl=en).


## Core References

https://github.com/SceneView/sceneview-android  
https://firebase.google.com/docs/firestore  
https://developers.google.com/ar/develop/cloud-anchors  
https://developers.google.com/ar/develop/geospatial
