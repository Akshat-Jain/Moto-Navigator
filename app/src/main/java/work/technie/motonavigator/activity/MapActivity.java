package work.technie.motonavigator.activity;

import android.Manifest;
import android.animation.TypeEvaluator;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationListener;
import com.mapbox.mapboxsdk.location.LocationServices;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.services.Constants;
import com.mapbox.services.android.geocoder.ui.GeocoderAutoCompleteView;
import com.mapbox.services.commons.ServicesException;
import com.mapbox.services.commons.geojson.LineString;
import com.mapbox.services.commons.models.Position;
import com.mapbox.services.directions.v5.DirectionsCriteria;
import com.mapbox.services.directions.v5.MapboxDirections;
import com.mapbox.services.directions.v5.models.DirectionsResponse;
import com.mapbox.services.directions.v5.models.DirectionsRoute;
import com.mapbox.services.directions.v5.models.LegStep;
import com.mapbox.services.directions.v5.models.RouteLeg;
import com.mapbox.services.directions.v5.models.StepManeuver;
import com.mapbox.services.geocoding.v5.GeocodingCriteria;
import com.mapbox.services.geocoding.v5.models.GeocodingFeature;

import java.util.List;
import java.util.Locale;
import java.util.Vector;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import work.technie.motonavigator.R;
import work.technie.motonavigator.data.Db;
import work.technie.motonavigator.data.DbContract;

/**
 * Created by anupam on 31/10/16.
 */

public class MapActivity extends BaseActivity {

    private final static String TAG = "MapFragment";
    private static final int PERMISSIONS_LOCATION = 0;
    FloatingActionButton floatingActionButtonA;
    FloatingActionButton floatingActionButtonB;
    LocationServices locationServices;
    private MapView mapView;
    private MapboxMap map;
    private DirectionsRoute currentRoute;
    private Marker markerDestination;
    private Marker markerOrigin;
    private Position origin;
    private Position destination;
    private Polyline routePolyLine;
    private Activity mActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        View hView = navigationView.getHeaderView(0);
        TextView nav_user_name = (TextView) hView.findViewById(R.id.user_name);
        nav_user_name.setText(String.format(Locale.ENGLISH, "Welcome %s", user.getDisplayName()));
        TextView nav_user_email = (TextView) hView.findViewById(R.id.user_email);
        nav_user_email.setText(user.getEmail());

        mActivity = this;

        locationServices = LocationServices.getLocationServices(this);

        mapView = (MapView) findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        final AppBarLayout appBarLayout = (AppBarLayout) findViewById(R.id.app_bar_layout);

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                map = mapboxMap;
                mapboxMap.setOnMapClickListener(new MapboxMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(@NonNull LatLng point) {

                        appBarLayout.setExpanded(false);
                    }
                });
            }
        });


        final GeocoderAutoCompleteView autocompleteStart = (GeocoderAutoCompleteView) findViewById(R.id.query_start);
        autocompleteStart.setAccessToken(getString(R.string.PUBLIC_TOKEN));
        autocompleteStart.setType(GeocodingCriteria.TYPE_POI);
        autocompleteStart.setOnFeatureListener(new GeocoderAutoCompleteView.OnFeatureListener() {
            @Override
            public void OnFeatureClick(GeocodingFeature feature) {
                Position position = feature.asPosition();
                updateMap(position.getLatitude(), position.getLongitude(), true);
            }
        });


        final GeocoderAutoCompleteView autocompleteDestination = (GeocoderAutoCompleteView) findViewById(R.id.query_destination);
        autocompleteDestination.setAccessToken(getString(R.string.PUBLIC_TOKEN));
        autocompleteDestination.setType(GeocodingCriteria.TYPE_POI);
        autocompleteDestination.setOnFeatureListener(new GeocoderAutoCompleteView.OnFeatureListener() {
            @Override
            public void OnFeatureClick(GeocodingFeature feature) {
                Position position = feature.asPosition();
                updateMap(position.getLatitude(), position.getLongitude(), false);
            }
        });


        floatingActionButtonA = (FloatingActionButton) findViewById(R.id.location_toggle_fab1);
        floatingActionButtonA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (map != null) {
                    toggleGps(!map.isMyLocationEnabled(), autocompleteStart);
                }
            }
        });

        floatingActionButtonB = (FloatingActionButton) findViewById(R.id.location_toggle_fab2);
        floatingActionButtonB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (map != null) {
                    toggleGps(!map.isMyLocationEnabled(), autocompleteStart);
                }
            }
        });

        ImageButton getCurrentLoc = (ImageButton) findViewById(R.id.get_current_location);

        getCurrentLoc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Location loc = map.getMyLocation();
                if (loc == null) {
                    enableLocation(true, autocompleteStart);
                }
                autocompleteStart.requestFocus();
            }
        });

        ImageButton swapLoc = (ImageButton) findViewById(R.id.swap_endpoints);
        swapLoc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String startStr = autocompleteStart.getText().toString();
                String destinationStr = autocompleteDestination.getText().toString();
                autocompleteStart.setText(destinationStr);
                autocompleteDestination.setText(startStr);
                Position tmp = origin;
                origin = destination;
                destination = tmp;
                if (routePolyLine != null) {
                    map.removePolyline(routePolyLine);
                }
                if (markerOrigin != null) {
                    map.removeMarker(markerOrigin);
                }

                if (markerDestination != null) {
                    map.removeMarker(markerDestination);
                }

                markerDestination = map.addMarker(new MarkerOptions()
                        .position(new LatLng(destination.getLatitude(), destination.getLongitude())).title("Destination"));

                markerOrigin = map.addMarker(new MarkerOptions()
                        .position(new LatLng(origin.getLatitude(), origin.getLongitude())).title("Origin"));

            }
        });

        Button walkPath = (Button) findViewById(R.id.walk);
        walkPath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (map != null) {
                    try {
                        if (validateForm(autocompleteStart, autocompleteDestination)) {
                            getRoute(origin, destination, DirectionsCriteria.PROFILE_WALKING);
                        }
                    } catch (ServicesException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        Button bikePath = (Button) findViewById(R.id.cycle);
        bikePath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (map != null) {
                    try {
                        if (validateForm(autocompleteStart, autocompleteDestination)) {
                            getRoute(origin, destination, DirectionsCriteria.PROFILE_CYCLING);
                        }
                    } catch (ServicesException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        Button carPath = (Button) findViewById(R.id.drive);
        carPath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (map != null) {
                    try {
                        if (validateForm(autocompleteStart, autocompleteDestination)) {
                            getRoute(origin, destination, DirectionsCriteria.PROFILE_DRIVING);
                        }
                    } catch (ServicesException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        final CollapsingToolbarLayout collapsingToolbarLayout = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);

        collapsingToolbarLayout.setTitle(" ");
        findViewById(R.id.location_toggle_fab1).setVisibility(View.VISIBLE);
        findViewById(R.id.location_toggle_fab2).setVisibility(View.INVISIBLE);

        appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            boolean isShow = false;
            int scrollRange = -1;

            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                if (scrollRange == -1) {
                    scrollRange = appBarLayout.getTotalScrollRange();
                }
                if (scrollRange + verticalOffset == 0) {
                    collapsingToolbarLayout.setTitle("Choose Destination...");
                    floatingActionButtonA.setVisibility(View.INVISIBLE);
                    floatingActionButtonB.setVisibility(View.VISIBLE);
                    isShow = true;
                } else if (isShow) {
                    collapsingToolbarLayout.setTitle(" ");//carefull there should a space between double quote otherwise it wont work
                    isShow = false;
                    floatingActionButtonA.setVisibility(View.VISIBLE);
                    floatingActionButtonB.setVisibility(View.INVISIBLE);

                }
            }
        });

        toolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appBarLayout.setExpanded(true, true);
            }
        });

        appBarLayout.setExpanded(true, true);
        floatingActionButtonA.setVisibility(View.VISIBLE);
        floatingActionButtonB.setVisibility(View.INVISIBLE);
    }

    private boolean validateForm(GeocoderAutoCompleteView autocompleteStart, GeocoderAutoCompleteView autoCompleteDestination) {
        boolean valid = true;

        String origin = autocompleteStart.getText().toString();
        if (TextUtils.isEmpty(origin)) {
            autocompleteStart.setError(Html.fromHtml("<font color='Red'>Required.</font>"));
            valid = false;
        } else {
            autocompleteStart.setError(null);
        }

        String destination = autoCompleteDestination.getText().toString();
        if (TextUtils.isEmpty(destination)) {
            autoCompleteDestination.setError(Html.fromHtml("<font color='Red'>Required.</font>"));
            valid = false;
        } else {
            autoCompleteDestination.setError(null);
        }

        return valid;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return super.onOptionsItemSelected(item);
    }

    private void updateMap(double latitude, double longitude, boolean isOrigin) {
        if (routePolyLine != null) {
            map.removePolyline(routePolyLine);
        }
        if (!isOrigin) {
            if (markerDestination != null) {
                map.removeMarker(markerDestination);
            }
            markerDestination = map.addMarker(new MarkerOptions()
                    .position(new LatLng(latitude, longitude)).title("Destination"));
            destination = Position.fromCoordinates(longitude, latitude);
        } else {
            if (markerOrigin != null) {
                map.removeMarker(markerOrigin);
            }
            markerOrigin = map.addMarker(new MarkerOptions()
                    .position(new LatLng(latitude, longitude)).title("Origin"));
            origin = Position.fromCoordinates(longitude, latitude);
        }

        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(new LatLng(latitude, longitude))
                .zoom(15)
                .build();
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 5000, null);
    }

    private void getRoute(Position origin, Position destination, final String profile) throws ServicesException {

        if (null == origin) {
            Log.e(TAG, "Origin empty");
            Toast.makeText(mActivity, "Set Origin!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (null == destination) {
            Log.e(TAG, "Destination empty");
            Toast.makeText(mActivity, "Set Destination!", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Origin :: lat " + origin.getLatitude() + " long " + origin.getLongitude());
        Log.d(TAG, "Destination :: lat " + destination.getLatitude() + " long " + destination.getLongitude());

        MapboxDirections client = new MapboxDirections.Builder()
                .setOrigin(origin)
                .setDestination(destination)
                .setProfile(profile)
                .setSteps(true)
                .setAccessToken(getString(R.string.PUBLIC_TOKEN))
                .build();

        client.enqueueCall(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                // You can get the generic HTTP info about the response
                Log.d(TAG, "Response code: " + response.code());
                if (response.body() == null) {
                    Log.e(TAG, "No routes found, make sure you set the right user and access token.");
                    return;
                }


                RouteLeg mLeg = response.body().getRoutes().get(0).getLegs().get(0);

                ContentValues mRoute = new ContentValues();
                mRoute.put(DbContract.Route.DISTANCE, String.valueOf(mLeg.getDistance()));
                mRoute.put(DbContract.Route.DURATION, String.valueOf(mLeg.getDuration()));

                Vector<ContentValues> cVVectorSteps = new Vector<>();
                for (LegStep mSteps : mLeg.getSteps()) {

                    ContentValues steps = new ContentValues();
                    StepManeuver maneuver = mSteps.getManeuver();
                    steps.put(DbContract.Steps.BEARING_BEFORE, String.valueOf(maneuver.getBearingBefore()));
                    steps.put(DbContract.Steps.BEARING_AFTER, String.valueOf(maneuver.getBearingAfter()));
                    steps.put(DbContract.Steps.LOCATION_LAT, String.valueOf(maneuver.getLocation()[1]));
                    steps.put(DbContract.Steps.LOCATION_LONG, String.valueOf(maneuver.getLocation()[0]));
                    steps.put(DbContract.Steps.TYPE, maneuver.getType());
                    steps.put(DbContract.Steps.INSTRUCTION, maneuver.getInstruction());
                    steps.put(DbContract.Steps.MODE, mSteps.getMode());
                    steps.put(DbContract.Steps.DURATION, String.valueOf(mSteps.getDuration()));
                    steps.put(DbContract.Steps.NAME, mSteps.getName());
                    steps.put(DbContract.Steps.DISTANCE, String.valueOf(mSteps.getDistance()));

                    cVVectorSteps.add(steps);
                }
                if (cVVectorSteps.size() > 0) {
                    ContentValues[] cvArray = new ContentValues[cVVectorSteps.size()];
                    cVVectorSteps.toArray(cvArray);
                    Db db = new Db(mActivity);
                    db.open();
                    db.clearDatabaseTable(DbContract.Steps.TABLE_NAME);
                    db.clearDatabaseTable(DbContract.Route.TABLE_NAME);
                    db.bulkInsertRouteSteps(mRoute, cvArray);
                    db.close();
                }

                // Print some info about the route
                currentRoute = response.body().getRoutes().get(0);
                Log.d(TAG, "Distance: " + currentRoute.getDistance() + " " + currentRoute.getLegs().size());
                Toast.makeText(mActivity, "Route is " + currentRoute.getDistance() + " meters long.", Toast.LENGTH_SHORT).show();

                // Draw the route on the map
                drawRoute(currentRoute, profile);
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                Log.e(TAG, "Error: " + t.getMessage());
                Toast.makeText(mActivity, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void drawRoute(DirectionsRoute route, String profile) {
        // Convert LineString coordinates into LatLng[]
        LineString lineString = LineString.fromPolyline(route.getGeometry(), Constants.OSRM_PRECISION_V5);
        List<Position> coordinates = lineString.getCoordinates();
        LatLng[] points = new LatLng[coordinates.size()];
        for (int i = 0; i < coordinates.size(); i++) {
            points[i] = new LatLng(
                    coordinates.get(i).getLatitude(),
                    coordinates.get(i).getLongitude());
        }

        // Draw Points on MapView
        int color;
        if (profile.equals(DirectionsCriteria.PROFILE_CYCLING)) {
            color = ContextCompat.getColor(mActivity, R.color.polyBike);
        } else if (profile.equals(DirectionsCriteria.PROFILE_DRIVING)) {
            color = ContextCompat.getColor(mActivity, R.color.polyCar);
        } else {
            color = ContextCompat.getColor(mActivity, R.color.polyWalk);
        }

        if (routePolyLine != null) {
            map.removePolyline(routePolyLine);
        }

        routePolyLine = map.addPolyline(new PolylineOptions()
                .add(points)
                .color(color)
                .width(5));
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @UiThread
    public void toggleGps(boolean enableGps, GeocoderAutoCompleteView autoCompleteView) {
        if (enableGps) {
            // Check if user has granted location permission
            if (!locationServices.areLocationPermissionsGranted()) {
                ActivityCompat.requestPermissions(mActivity, new String[]{
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_LOCATION);
            } else {
                enableLocation(true, autoCompleteView);
            }
        } else {
            enableLocation(false, autoCompleteView);
        }
    }

    private Location enableLocation(boolean enabled, final GeocoderAutoCompleteView autoCompleteStart) {
        final Location[] newLocation = {null};
        final FlagGPSOneTime gps = new FlagGPSOneTime();
        if (enabled) {
            map.setMyLocationEnabled(true);
            locationServices.addLocationListener(new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (location != null && gps.flag) {
                        newLocation[0] = location;
                        if (routePolyLine != null) {
                            map.removePolyline(routePolyLine);
                        }
                        if (markerOrigin != null) {
                            map.removeMarker(markerOrigin);
                        }
                            IconFactory iconFactory = IconFactory.getInstance(mActivity);
                            Drawable iconDrawable = ContextCompat.getDrawable(mActivity, R.drawable.default_marker);
                            Icon icon = iconFactory.fromDrawable(iconDrawable);

                            markerOrigin = map.addMarker(new MarkerOptions()
                                    .position(new LatLng(location.getLatitude(), location.getLongitude())).title("Origin").icon(icon));


                        markerOrigin.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
                        origin = Position.fromCoordinates(location.getLongitude(), location.getLatitude());
                        map.setCameraPosition(new CameraPosition.Builder()
                                .target(new LatLng(location))
                                .zoom(16)
                                .build());
                            Geocoder geocoder = new Geocoder(mActivity, Locale.getDefault());
                            try {
                                List<Address> address = geocoder.getFromLocation(
                                        location.getLatitude(),
                                        location.getLongitude(),
                                        1);
                                autoCompleteStart.setText(address.get(0).getFeatureName());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    gps.flag = false;
                    map.setMyLocationEnabled(false);

                }
            });
            floatingActionButtonA.setImageResource(R.drawable.ic_location_disabled_24dp);
            floatingActionButtonB.setImageResource(R.drawable.ic_location_disabled_24dp);
        } else {
            floatingActionButtonA.setImageResource(R.drawable.ic_my_location_24dp);
            floatingActionButtonB.setImageResource(R.drawable.ic_my_location_24dp);
            map.setMyLocationEnabled(false);
        }
        // Enable or disable the location layer on the map
        return newLocation[0];
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_LOCATION: {
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    enableLocation(true, null);
                }
            }
        }
    }

    private static class LatLngEvaluator implements TypeEvaluator<LatLng> {
        // Method is used to interpolate the marker animation.

        private LatLng latLng = new LatLng();

        @Override
        public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
            latLng.setLatitude(startValue.getLatitude() +
                    ((endValue.getLatitude() - startValue.getLatitude()) * fraction));
            latLng.setLongitude(startValue.getLongitude() +
                    ((endValue.getLongitude() - startValue.getLongitude()) * fraction));
            return latLng;
        }
    }

    class FlagGPSOneTime {
        public boolean flag;

        FlagGPSOneTime() {
            this.flag = true;
        }
    }
}
