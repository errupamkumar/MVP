package com.srmecotech.ecoride.geo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoTest {

    private static final GeoPoint BALEWADI = new GeoPoint(18.570719, 73.774308);
    private static final GeoPoint UNIVERSITY = new GeoPoint(18.552844, 73.826644);

    @Test
    void haversineMatchesAKnownDistance() {
        // Balewadi High Street to Savitribai Phule Pune University: about 5.9 km as the crow flies.
        assertThat(GeoMath.distanceM(BALEWADI, UNIVERSITY)).isCloseTo(5_880, within(80.0));
    }

    @Test
    void polylineRoundTripsAtFivePlaces() {
        List<GeoPoint> points = List.of(BALEWADI, new GeoPoint(18.5612, 73.80001), UNIVERSITY);
        List<GeoPoint> back = Polyline.decode(Polyline.encode(points));
        assertThat(back).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(back.get(i).lat()).isCloseTo(points.get(i).lat(), within(1e-5));
            assertThat(back.get(i).lng()).isCloseTo(points.get(i).lng(), within(1e-5));
        }
    }

    @Test
    void decodesTheGooglePolylineExample() {
        // The worked example from Google's polyline documentation.
        List<GeoPoint> p = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertThat(p).hasSize(3);
        assertThat(p.get(0).lat()).isCloseTo(38.5, within(1e-6));
        assertThat(p.get(0).lng()).isCloseTo(-120.2, within(1e-6));
        assertThat(p.get(2).lat()).isCloseTo(43.252, within(1e-6));
        assertThat(p.get(2).lng()).isCloseTo(-126.453, within(1e-6));
    }

    @Test
    void trackerMeasuresTheDistanceLeftAlongTheRoute() {
        // An L-shaped route: 1 km east, then 1 km north.
        GeoPoint start = new GeoPoint(18.5, 73.8);
        GeoPoint corner = GeoMath.offset(start, 1000, 90);
        GeoPoint end = GeoMath.offset(corner, 1000, 0);
        RouteTracker tracker = new RouteTracker(List.of(start, corner, end));
        assertThat(tracker.totalM()).isCloseTo(2000, within(2.0));

        // Halfway along the first leg: 1.5 km left by road, though the end is ~1.1 km away straight.
        RouteTracker.Progress p = tracker.locate(GeoMath.offset(start, 500, 90));
        assertThat(p.remainingM()).isCloseTo(1500, within(5.0));
        assertThat(p.offRouteM()).isLessThan(1);

        // A fix 40 m beside the route still snaps onto it.
        GeoPoint beside = GeoMath.offset(GeoMath.offset(corner, 300, 0), 40, 90);
        RouteTracker.Progress q = tracker.locate(beside);
        assertThat(q.remainingM()).isCloseTo(700, within(5.0));
        assertThat(q.offRouteM()).isCloseTo(40, within(2.0));
    }

    @Test
    void pointAtWalksTheRoute() {
        GeoPoint start = new GeoPoint(18.5, 73.8);
        GeoPoint end = GeoMath.offset(start, 1000, 45);
        RouteTracker tracker = new RouteTracker(List.of(start, end));
        assertThat(GeoMath.distanceM(tracker.pointAt(250), start)).isCloseTo(250, within(1.0));
        assertThat(tracker.headingAt(500)).isBetween(44, 46);
        assertThat(tracker.pointAt(5000)).isEqualTo(end);
    }
}
