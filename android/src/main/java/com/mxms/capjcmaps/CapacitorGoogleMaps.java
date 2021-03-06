package com.mxms.capjcmaps;

import android.Manifest;
import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.google.android.libraries.maps.CameraUpdateFactory;
import com.google.android.libraries.maps.GoogleMap;
import com.google.android.libraries.maps.GoogleMapOptions;
import com.google.android.libraries.maps.MapView;
import com.google.android.libraries.maps.OnMapReadyCallback;
import com.google.android.libraries.maps.UiSettings;
import com.google.android.libraries.maps.CameraUpdate;
import com.google.android.libraries.maps.model.BitmapDescriptorFactory;
import com.google.android.libraries.maps.model.CameraPosition;
import com.google.android.libraries.maps.model.CircleOptions;
import com.google.android.libraries.maps.model.LatLngBounds;
import com.google.android.libraries.maps.model.LatLng;
import com.google.android.libraries.maps.model.MapStyleOptions;
import com.google.android.libraries.maps.model.Marker;
import com.google.android.libraries.maps.model.MarkerOptions;
import com.google.android.libraries.maps.model.PointOfInterest;
import com.google.android.libraries.maps.model.PolygonOptions;
import com.google.android.libraries.maps.model.Polyline;
import com.google.android.libraries.maps.model.PolylineOptions;
import com.google.android.libraries.maps.model.SquareCap;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.graphics.Color;

@NativePlugin()
public class CapacitorGoogleMaps extends Plugin implements OnMapReadyCallback, GoogleMap.OnMyLocationClickListener, GoogleMap.OnMyLocationButtonClickListener {

    private MapView mapView;
    GoogleMap googleMap;
    Integer mapViewParentId;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean locationPermissionGranted;
    Integer DEFAULT_WIDTH = 500;
    Integer DEFAULT_HEIGHT = 500;
    Float DEFAULT_ZOOM = 12.0f;

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        notifyListeners("onMapReady", null);
    }

    @Override
    protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.handleRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    locationPermissionGranted = true;
                }
            }
        }

        if (locationPermissionGranted) {
            notifyListeners("onLocationPermissionGranted", null);
        }
    }

    @PluginMethod()
    public void initialize(PluginCall call) {
        /*
         *  TODO: Check location permissions and API key
         */
        call.success();
    }

    @PluginMethod()
    public void requestLocationPermission(PluginCall call) {
        ActivityCompat.requestPermissions(getBridge().getActivity(),
                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);

        JSObject result = new JSObject();
        result.put("locationPermissionRequested", true);

        call.resolve(result);
    }

    @PluginMethod()
    public void hasLocationPermission(PluginCall call) {
        Context ctx = getBridge().getContext();

        if (ContextCompat.checkSelfPermission(ctx,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
        } else {
            locationPermissionGranted = false;
        }

        JSObject result = new JSObject();
        result.put("locationPermissionGranted", locationPermissionGranted);

        call.resolve(result);
    }

    @PluginMethod()
    public void create(PluginCall call) {
        final Integer width = call.getInt("width", DEFAULT_WIDTH);
        final Integer height = call.getInt("height", DEFAULT_HEIGHT);
        final Integer x = call.getInt("x", 0);
        final Integer y = call.getInt("y", 0);

        final Float zoom = call.getFloat("zoom", DEFAULT_ZOOM);
        final Double latitude = call.getDouble("latitude");
        final Double longitude = call.getDouble("longitude");

        final boolean liteMode = call.getBoolean("enabled", false);

        final CapacitorGoogleMaps ctx = this;

        JSObject ret = new JSObject();

        getBridge().getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

              StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
              StrictMode.setThreadPolicy(policy);

                LatLng latLng = new LatLng(latitude, longitude);
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(latLng)
                        .zoom(zoom)
                        .build();

                GoogleMapOptions googleMapOptions = new GoogleMapOptions();
                googleMapOptions.camera(cameraPosition);
                googleMapOptions.liteMode(liteMode);

                FrameLayout mapViewParent = new FrameLayout(getBridge().getContext());
                mapViewParentId = View.generateViewId();
                mapViewParent.setId(mapViewParentId);

                mapView = new MapView(getBridge().getContext(), googleMapOptions);

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(getScaledPixels(width), getScaledPixels(height));
                lp.topMargin = getScaledPixels(y);
                lp.leftMargin = getScaledPixels(x);

                mapView.setLayoutParams(lp);

                mapViewParent.addView(mapView);

                ((ViewGroup) getBridge().getWebView().getParent()).addView(mapViewParent);

                mapView.onCreate(null);
                mapView.getMapAsync(ctx);


                // map.setInfoWindowAdapter(new PopupAdapter(getLayoutInflater()));
                // map.setOnInfoWindowClickListener(this);
            }
        });
        ret.put("created", true);
        call.success(ret);
    }

    @PluginMethod()
    public void addMarker(PluginCall call) {

        final Double latitude = call.getDouble("latitude", 0d);
        final Double longitude = call.getDouble("longitude", 0d);
        final Float opacity = call.getFloat("opacity", 1.0f);
        final String title = call.getString("title", "");
        final String snippet = call.getString("snippet", "");
        final Boolean isFlat = call.getBoolean("isFlat", false);
        final String iconUrl = call.getString("iconUrl", "");
        final Boolean isSpeeding = call.getBoolean("speeding", false);

        getBridge().getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                LatLng latLng = new LatLng(latitude, longitude);
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(latLng);
                markerOptions.alpha(opacity);
                markerOptions.title(title);
                markerOptions.snippet(snippet);
                markerOptions.flat(isFlat);
                if(iconUrl != "") {
                  markerOptions.icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromURL(iconUrl)));
                } else {
                  if(isSpeeding == true) {
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                  } else {
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
                  }
                }
                googleMap.addMarker(markerOptions);
            }
        });

        call.resolve();
    }

    public Bitmap getBitmapFromURL(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            return myBitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @PluginMethod()
    public void addPolyline(final PluginCall call) {
        final JSArray points = call.getArray("points", new JSArray());

        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                PolylineOptions polylineOptions;
                final Polyline greyPolyLine, blackPolyline;
                final ValueAnimator polylineAnimator;

                final List<LatLng> finalPoints = new ArrayList<LatLng>();

                for (int i = 0; i < points.length(); i++) {
                    try {
                        JSONObject point = points.getJSONObject(i);
                        LatLng latLng = new LatLng(point.getDouble("latitude"), point.getDouble("longitude"));
                        finalPoints.add(latLng);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                if ((int)finalPoints.size() > 0) {
                    polylineOptions = new PolylineOptions();
                    polylineOptions.color(0xffE5E6EA);
                    polylineOptions.width(8);
                    polylineOptions.startCap(new SquareCap());
                    polylineOptions.endCap(new SquareCap());
                    polylineOptions.jointType(2);
                    polylineOptions.addAll(finalPoints);
                    greyPolyLine = googleMap.addPolyline(polylineOptions);

                    polylineOptions = new PolylineOptions();
                    polylineOptions.width(8);
                    polylineOptions.color(0xff0077C5);
                    polylineOptions.startCap(new SquareCap());
                    polylineOptions.endCap(new SquareCap());
                    polylineOptions.zIndex(15);
                    polylineOptions.jointType(2);
                    polylineOptions.add(finalPoints.get(0));

                    blackPolyline = googleMap.addPolyline(polylineOptions);

                    polylineAnimator = ValueAnimator.ofInt(0, 100);
                    int multiplier = 200;
                    if((int)finalPoints.size() < 25) {
                      multiplier = 200;
                    } else {
                      multiplier = 50;
                    }
                    polylineAnimator.setDuration((int)(multiplier*(int)finalPoints.size()));
                    polylineAnimator.setInterpolator(new LinearInterpolator());

                    polylineAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator valueAnimator) {
                            int percentValue = (int) valueAnimator.getAnimatedValue();
                            List<LatLng> points = greyPolyLine.getPoints();
                            int size = points.size();
                            if(percentValue > 0) {
                                int newPoints = (int) (size * (percentValue / 100.0f));
                                List<LatLng> p = points.subList(0, newPoints);
                                if(p.size() > 0) {
                                    blackPolyline.setPoints(p);
                                }
                            }
                        }
                    });

                    polylineAnimator.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            blackPolyline.setPoints(Collections.singletonList(finalPoints.get(0)));
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            List<LatLng> greyLatLng = greyPolyLine.getPoints();
                            if (greyLatLng != null) {
                                greyLatLng.clear();
                            }
                            polylineAnimator.start();
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            polylineAnimator.cancel();
                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {
                        }
                    });

                    polylineAnimator.start();
                }
                call.resolve();
            }
        });
    }

    @PluginMethod()
    public void addPolygon(final PluginCall call) {
        final JSArray points = call.getArray("points", new JSArray());

        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                PolygonOptions polygonOptions = new PolygonOptions();

                for (int i = 0; i < points.length(); i++) {
                    try {
                        JSONObject point = points.getJSONObject(i);
                        LatLng latLng = new LatLng(point.getDouble("latitude"), point.getDouble("longitude"));
                        polygonOptions.add(latLng);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                googleMap.addPolygon(polygonOptions);
                call.resolve();
            }
        });
    }

    @PluginMethod()
    public void addCircle(final PluginCall call) {
        final int radius = call.getInt("radius", 0);
        final JSONObject center = call.getObject("center", new JSObject());

        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                CircleOptions circleOptions = new CircleOptions();
                circleOptions.radius(radius);
                try {
                    circleOptions.center(new LatLng(center.getDouble("latitude"), center.getDouble("longitude")));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                googleMap.addCircle(circleOptions);

                call.resolve();
            }
        });
    }

    @PluginMethod()
    public void setMapType(PluginCall call) {

        String specifiedMapType = call.getString("type", "normal");
        Integer mapType;

        switch (specifiedMapType) {
            case "hybrid":
                mapType = GoogleMap.MAP_TYPE_HYBRID;
                break;
            case "satellite":
                mapType = GoogleMap.MAP_TYPE_SATELLITE;
                break;
            case "terrain":
                mapType = GoogleMap.MAP_TYPE_TERRAIN;
                break;
            case "none":
                mapType = GoogleMap.MAP_TYPE_NONE;
                break;
            default:
                mapType = GoogleMap.MAP_TYPE_NORMAL;
        }

        final Integer selectedMapType = mapType;
        getBridge().getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                googleMap.setMapType(selectedMapType);
            }

        });

        call.success();
    }

    @PluginMethod()
    public void setIndoorEnabled(PluginCall call) {
        final Boolean indoorEnabled = call.getBoolean("enabled", false);
        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                googleMap.setIndoorEnabled(indoorEnabled);
            }
        });

        call.success();
    }

    @PluginMethod()
    public void setTrafficEnabled(PluginCall call) {
        final Boolean trafficEnabled = call.getBoolean("enabled", false);
        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                googleMap.setTrafficEnabled(trafficEnabled);
            }
        });

        call.success();
    }

    @PluginMethod()
    public void padding(PluginCall call) {
        final Integer top = call.getInt("top", 0);
        final Integer left = call.getInt("left", 0);
        final Integer bottom = call.getInt("bottom", 0);
        final Integer right = call.getInt("right", 0);

        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                googleMap.setPadding(left, top, right, bottom);
            }
        });

        call.success();
    }

    @PluginMethod()
    public void clear(PluginCall call) {
        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                googleMap.clear();
            }
        });

        call.success();
    }

    @PluginMethod()
    public void close(PluginCall call) {
        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                View viewToRemove = ((ViewGroup) getBridge().getWebView().getParent()).findViewById(mapViewParentId);
                if(viewToRemove != null) {
                    ((ViewGroup) getBridge().getWebView().getParent()).removeViewInLayout(viewToRemove);
                }
            }
        });
    }

    @PluginMethod()
    public void hide(PluginCall call) {
        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                View viewToHide = ((ViewGroup) getBridge().getWebView().getParent()).findViewById(mapViewParentId);
                viewToHide.setVisibility(View.INVISIBLE);
            }
        });
    }

    @PluginMethod()
    public void show(PluginCall call) {
        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                View viewToShow = ((ViewGroup) getBridge().getWebView().getParent()).findViewById(mapViewParentId);
                viewToShow.setVisibility(View.VISIBLE);
            }
        });
    }

    @PluginMethod()
    public void reverseGeocodeCoordinate(final PluginCall call) {
        final Double latitude = call.getDouble("latitude", 0.0);
        final Double longitude = call.getDouble("longitude", 0.0);

        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                /*
                 * TODO: Check if can be done without adding Places SDK
                 *
                 */

                Geocoder geocoder = new Geocoder(getContext());
                try {
                    List<Address> addressList = geocoder.getFromLocation(latitude, longitude, 5);

                    JSObject results = new JSObject();

                    int index = 0;
                    for (Address address : addressList) {
                        JSObject addressObject = new JSObject();
                        addressObject.put("administrativeArea", address.getAdminArea());
                        addressObject.put("lines", address.getAddressLine(0));
                        addressObject.put("country", address.getCountryName());
                        addressObject.put("locality", address.getLocality());
                        addressObject.put("subLocality", address.getSubLocality());
                        addressObject.put("thoroughFare", address.getThoroughfare());

                        results.put(String.valueOf(index++), addressObject);
                    }
                    call.success(results);
                } catch (IOException e) {
                    call.error("Error in Geocode!");
                }
            }
        });
    }

    @PluginMethod()
    public void settings(final PluginCall call) {

        final boolean allowScrollGesturesDuringRotateOrZoom = call.getBoolean("allowScrollGesturesDuringRotateOrZoom", true);

        final boolean compassButton = call.getBoolean("compassButton", false);
        final boolean zoomButton = call.getBoolean("zoomButton", true);
        final boolean myLocationButton = call.getBoolean("myLocationButton", false);

        boolean consumesGesturesInView = call.getBoolean("consumesGesturesInView", true);
        final boolean indoorPicker = call.getBoolean("indoorPicker", false);

        final boolean rotateGestures = call.getBoolean("rotateGestures", true);
        final boolean scrollGestures = call.getBoolean("scrollGestures", true);
        final boolean tiltGestures = call.getBoolean("tiltGestures", true);
        final boolean zoomGestures = call.getBoolean("zoomGestures", true);

        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                UiSettings googleMapUISettings = googleMap.getUiSettings();
                googleMapUISettings.setScrollGesturesEnabledDuringRotateOrZoom(allowScrollGesturesDuringRotateOrZoom);
                googleMapUISettings.setCompassEnabled(compassButton);
                googleMapUISettings.setIndoorLevelPickerEnabled(indoorPicker);
                googleMapUISettings.setMyLocationButtonEnabled(myLocationButton);
                googleMapUISettings.setRotateGesturesEnabled(rotateGestures);
                googleMapUISettings.setScrollGesturesEnabled(scrollGestures);
                googleMapUISettings.setTiltGesturesEnabled(tiltGestures);
                googleMapUISettings.setZoomGesturesEnabled(zoomGestures);
                googleMapUISettings.setZoomControlsEnabled(zoomButton);
                googleMapUISettings.setMyLocationButtonEnabled(true);
            }
        });

        call.resolve();
    }

    @PluginMethod()
    public void setCamera(PluginCall call) {

        final Float viewingAngle = call.getFloat("viewingAngle", 1.0f);
        final Float bearing = call.getFloat("bearing", 1.0f);
        final Integer zoom = call.getInt("zoom", 8);
        final Double latitude = call.getDouble("latitude", 100.000);
        final Double longitude = call.getDouble("longitude", -100.00);
        final Boolean animate = call.getBoolean("animate", false);
        final Integer animationDuration = call.getInt("animationDuration", 1);
        final JSArray coordinates = call.getArray("coordinates", new JSArray());

        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (coordinates.length() > 0) {
                  LatLngBounds.Builder b = new LatLngBounds.Builder();
                  for (int i = 0; i < coordinates.length(); i++) {
                      try {
                          JSONObject point = coordinates.getJSONObject(i);
                          LatLng latLng = new LatLng(point.getDouble("latitude"), point.getDouble("longitude"));
                          b.include(latLng);
                      } catch (JSONException e) {
                          e.printStackTrace();
                      }
                  }
                  LatLngBounds bounds = b.build();
                  // CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, 100);
                  // googleMap.animateCamera(cu, animationDuration*1000, null);
                   googleMap.setMaxZoomPreference(zoom);
                  if(coordinates.length() > 1) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), animationDuration*1000, null);
                  } else {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                    // googleMap.animateCamera(, animationDuration*1000, new GoogleMap.CancelableCallback(){
                    //     @Override
                    //     public void onCancel() {
                    //         //DO SOMETHING HERE IF YOU WANT TO REACT TO A USER TOUCH WHILE ANIMATING
                    //     }
                    //     @Override
                    //     public void onFinish() {
                            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
                                    .target(googleMap.getCameraPosition().target)
                                    .zoom(googleMap.getCameraPosition().zoom)
                                    .bearing(bearing)
                                    .tilt(35.5f)
                                    .build()));
                        // }
                    // });
                  }
                  // CameraUpdateFactory.newLatLngZoom(bounds.getCenter(),zoom)
                }
//                  else {
//
//                     /*
//                      * TODO: Implement animationDuration etc when no coordinates are specified
//                      * */
//
//                     CameraPosition cameraPosition = new CameraPosition.Builder()
//                     .target(new LatLng(latitude, longitude))
//                     .zoom(zoom)
// //                    .tilt(viewingAngle)
//                     .tilt(65.5f)
//                     .bearing(bearing)
//                     .build();
//
//                   if (animate) {
//                       googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), animationDuration*1000, new GoogleMap.CancelableCallback(){
//                           @Override
//                           public void onCancel() {
//                               //DO SOMETHING HERE IF YOU WANT TO REACT TO A USER TOUCH WHILE ANIMATING
//                           }
//                           @Override
//                           public void onFinish() {
//                               googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
//                                       .target(googleMap.getCameraPosition().target)
//                                       .zoom(googleMap.getCameraPosition().zoom)
//                                       .bearing(bearing)
//                                       .tilt(65.5f)
//                                       .build()));
//                           }
//                       });
//                   } else {
//                       googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
//                   }
//                 }

            }
        });

        call.resolve();
    }

    @PluginMethod()
    public void setMapStyle(PluginCall call) {
        /*
            https://mapstyle.withgoogle.com/
        */
        final String mapStyle = call.getString("jsonString", "");

        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                MapStyleOptions mapStyleOptions = new MapStyleOptions(mapStyle);
                googleMap.setMapStyle(mapStyleOptions);
            }
        });
    }

    @PluginMethod()
    public void setOnMyLocationButtonClickListener(PluginCall call) {
        final CapacitorGoogleMaps ctx = this;

        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                googleMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
                    @Override
                    public boolean onMyLocationButtonClick() {
                        ctx.onMyLocationButtonClick();
                        return false;
                    }
                });
            }
        });

        call.resolve();
    }

    @PluginMethod()
    public void setOnMyLocationClickListener(PluginCall call) {
        final CapacitorGoogleMaps ctx = this;
        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                googleMap.setOnMyLocationClickListener(new GoogleMap.OnMyLocationClickListener() {
                    @Override
                    public void onMyLocationClick(@NonNull Location location) {
                        ctx.onMyLocationClick(location);
                    }
                });
            }
        });

        call.resolve();
    }

    @PluginMethod()
    public void setOnMarkerClickListener(PluginCall call) {

        final CapacitorGoogleMaps ctx = this;

        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker) {
                        ctx.onMarkerClick(marker);
                        return false;
                    }
                });
            }
        });

        call.resolve();
    }

    @PluginMethod()
    public void setOnPoiClickListener(PluginCall call) {

        final CapacitorGoogleMaps ctx = this;

        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                googleMap.setOnPoiClickListener(new GoogleMap.OnPoiClickListener() {
                    @Override
                    public void onPoiClick(PointOfInterest pointOfInterest) {
                        ctx.onPoiClick(pointOfInterest);
                    }
                });
            }
        });

        call.resolve();
    }

    @PluginMethod()
    public void setOnMapClickListener(PluginCall call) {

        final CapacitorGoogleMaps ctx = this;

        getBridge().executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(LatLng latLng) {
                        ctx.onMapClick(latLng);
                    }
                });
            }
        });

        call.resolve();
    }

    @PluginMethod()
    public void enableCurrentLocation(final PluginCall call) {

        final boolean enableLocation = call.getBoolean("enabled", false);
        final CapacitorGoogleMaps context = this;
        getBridge().executeOnMainThread(new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    call.error("Permission for location not granted");
                } else {
                    googleMap.setMyLocationEnabled(enableLocation);
                    googleMap.setOnMyLocationClickListener(context);
                    googleMap.setOnMyLocationButtonClickListener(context);
                    call.resolve();
                }
            }
        });
    }

    public void onPoiClick(PointOfInterest pointOfInterest) {
        JSObject result = new JSObject();
        JSObject location = new JSObject();
        JSObject coordinates = new JSObject();

        coordinates.put("latitude", pointOfInterest.latLng.latitude);
        coordinates.put("longitude", pointOfInterest.latLng.longitude);

        location.put("coordinates", coordinates);

        result.put("name", pointOfInterest.name);
        result.put("placeID", pointOfInterest.placeId);
        result.put("result", location);

        notifyListeners("didTapPOIWithPlaceID", result);
    }

    public void onMapClick(LatLng latLng) {
        JSObject result = new JSObject();
        JSObject location = new JSObject();
        JSObject coordinates = new JSObject();

        coordinates.put("latitude", latLng.latitude);
        coordinates.put("longitude", latLng.longitude);

        location.put("coordinates", coordinates);

        result.put("result", location);

        notifyListeners("didTapAt", location);
    }

    public void onMarkerClick(Marker marker) {
        JSObject result = new JSObject();
        JSObject location = new JSObject();
        JSObject coordinates = new JSObject();

        coordinates.put("latitude", marker.getPosition().latitude);
        coordinates.put("longitude", marker.getPosition().longitude);

        location.put("coordinates", coordinates);

        result.put("title", marker.getTitle());
        result.put("snippet", marker.getSnippet());
        result.put("result", location);

        notifyListeners("didTap", result);
    }

    public boolean onMyLocationButtonClick() {
        /*
         *  TODO: Add handler
         */
        return false;
    }

    public void onMyLocationClick(Location location) {
        JSObject result = new JSObject();

        result.put("latitude", location.getLatitude());
        result.put("longitude", location.getLongitude());

        notifyListeners("onMyLocationClick", result);
    }

    public int getScaledPixels(float pixels) {
        // Get the screen's density scale
        final float scale = getBridge().getActivity().getResources().getDisplayMetrics().density;
        // Convert the dps to pixels, based on density scale
        return (int) (pixels * scale + 0.5f);
    }

}
