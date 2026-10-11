package com.srmecotech.ecoride.demo;

import com.srmecotech.ecoride.geo.GeoPoint;

import java.util.List;

/**
 * The demo city: real Pune places (coordinates from OpenStreetMap), the demo accounts, and the
 * simulated fleet. Simulated accounts keep the maps and the dashboard alive; the named demo
 * accounts are the ones people sign in with.
 */
public final class DemoData {

    private DemoData() {
    }

    public static final String RIDER_PASSWORD = "Rider@123";
    public static final String DRIVER_PASSWORD = "Driver@123";
    public static final String ADMIN_PASSWORD = "Admin@123";
    public static final String SIMULATED_PASSWORD = "Simulated@123";

    public record Place(String name, String address, String category, double lat, double lng) {
        public GeoPoint point() {
            return new GeoPoint(lat, lng);
        }
    }

    public static final List<Place> PLACES = List.of(
            new Place("Pune International Airport", "Airport Road, Lohegaon, Pune", "AIRPORT", 18.580375, 73.918227),
            new Place("Pune Railway Station", "Sassoon Marg, Agarkar Nagar, Pune", "STATION", 18.528877, 73.874415),
            new Place("Shivajinagar", "Shivajinagar Railway Station Road, Shivajinagar, Pune", "STATION", 18.529459, 73.847825),
            new Place("Swargate Bus Stand", "Satara Road, Swargate, Pune", "STATION", 18.499134, 73.858045),
            new Place("Chinchwad Station", "Chinchwad, Pimpri-Chinchwad", "STATION", 18.639481, 73.791502),
            new Place("Phoenix Marketcity", "Viman Nagar Road, Viman Nagar, Pune", "MALL", 18.562043, 73.916663),
            new Place("Seasons Mall", "Magarpatta Road, Hadapsar, Pune", "MALL", 18.519035, 73.931288),
            new Place("Amanora Mall", "Amanora Town Centre, Hadapsar, Pune", "MALL", 18.517931, 73.933810),
            new Place("Westend Mall", "Mahadji Shinde Marg, Aundh, Pune", "MALL", 18.561655, 73.806819),
            new Place("Magarpatta City", "Magarpatta Inner Circle, Hadapsar, Pune", "WORK", 18.517013, 73.927024),
            new Place("EON IT Park", "EON IT Park Road, Kharadi, Pune", "WORK", 18.554128, 73.951580),
            new Place("World Trade Center Pune", "Fountain Road, Kharadi, Pune", "WORK", 18.551930, 73.948164),
            new Place("Hinjewadi Phase 1", "Rajiv Gandhi Infotech Park, Hinjawadi, Pune", "WORK", 18.591684, 73.734782),
            new Place("Hinjewadi Shivaji Chowk", "Hinjawadi Phase 2 Road, Hinjawadi, Pune", "WORK", 18.592393, 73.737349),
            new Place("Hinjewadi Phase 3", "Hinjawadi Phase 3, Maan, Pune", "WORK", 18.586088, 73.687338),
            new Place("Balewadi High Street", "Balewadi High Street, Balewadi, Pune", "AREA", 18.570719, 73.774308),
            new Place("Baner", "Baner Road, Baner, Pune", "AREA", 18.558994, 73.784523),
            new Place("Aundh", "ITI Road, Aundh, Pune", "AREA", 18.561883, 73.810196),
            new Place("Wakad", "Wakad, Pimpri-Chinchwad", "AREA", 18.602249, 73.764445),
            new Place("Kothrud", "Karve Road, Kothrud, Pune", "AREA", 18.507262, 73.805668),
            new Place("Koregaon Park", "North Main Road, Koregaon Park, Pune", "AREA", 18.536623, 73.893274),
            new Place("Kalyani Nagar", "Kalyani Nagar, Pune", "AREA", 18.548138, 73.902551),
            new Place("Viman Nagar", "Viman Nagar, Pune", "AREA", 18.570388, 73.913334),
            new Place("Hadapsar", "Solapur Road, Hadapsar, Pune", "AREA", 18.500774, 73.937915),
            new Place("Kondhwa", "Kondhwa, Pune", "AREA", 18.477991, 73.894063),
            new Place("Katraj", "Katraj, Pune", "AREA", 18.453679, 73.856320),
            new Place("Pimpri", "Old Mumbai-Pune Highway, Pimpri, Pimpri-Chinchwad", "AREA", 18.623185, 73.801993),
            new Place("Nigdi", "Nigdi, Pimpri-Chinchwad", "AREA", 18.659813, 73.777282),
            new Place("Pimple Saudagar", "Pimple Saudagar, Pimpri-Chinchwad", "AREA", 18.598155, 73.797766),
            new Place("Kharadi", "Kharadi, Pune", "AREA", 18.551276, 73.941658),
            new Place("Yerwada", "Jail Press Road, Yerawada, Pune", "AREA", 18.565632, 73.886576),
            new Place("Pashan", "Pashan, Pune", "AREA", 18.538670, 73.795254),
            new Place("Bavdhan", "Bavdhan, Pune", "AREA", 18.520954, 73.778087),
            new Place("Deccan Gymkhana", "Deccan Gymkhana, Pune", "AREA", 18.515893, 73.841160),
            new Place("Sinhagad Road", "Sinhagad Road, Anandnagar, Pune", "AREA", 18.474051, 73.822135),
            new Place("Bhosari", "Bhosari, Pimpri-Chinchwad", "AREA", 18.621009, 73.850130),
            new Place("Savitribai Phule Pune University", "Ganeshkhind Road, Pune", "EDU", 18.552844, 73.826644),
            new Place("Fergusson College", "FC Road, Shivajinagar, Pune", "EDU", 18.524148, 73.838592),
            new Place("College of Engineering Pune", "Wellesley Road, Shivajinagar, Pune", "EDU", 18.530621, 73.856833),
            new Place("Shaniwar Wada", "Shivaji Road, Kasba Peth, Pune", "LANDMARK", 18.519383, 73.855357),
            new Place("Dagdusheth Ganpati Temple", "Shivaji Road, Budhwar Peth, Pune", "LANDMARK", 18.516431, 73.856109),
            new Place("Aga Khan Palace", "Nagar Road, Kalyani Nagar, Pune", "LANDMARK", 18.552329, 73.901478),
            new Place("Ruby Hall Clinic", "Bund Garden Road, Pune", "HOSPITAL", 18.532718, 73.877749),
            new Place("Jehangir Hospital", "Bund Garden Road, Pune", "HOSPITAL", 18.530344, 73.876650));

    /** Fare cards (Pune, 2026 demo values; the operations team edits them in the admin console). */
    public record Product(String code, String name, String description, int capacity, double baseFare, double baseKm,
                          double perKm, double perMin, double minFare, int waitFreeMin, double waitPerMin,
                          double cancelFee, int sort) {
    }

    public static final List<Product> PRODUCTS = List.of(
            new Product("BIKE", "Bike", "Beat the traffic on a bike taxi", 1, 30, 3, 9, 0.5, 30, 3, 1, 25, 1),
            new Product("AUTO", "Auto", "Doorstep pickup in an auto rickshaw", 3, 45, 3, 15, 1, 45, 3, 1.5, 30, 2),
            new Product("MINI", "Mini", "Affordable hatchbacks", 4, 75, 3, 21, 1.5, 75, 3, 2, 50, 3),
            new Product("SEDAN", "Sedan", "Comfortable sedans, top drivers", 4, 95, 3, 25, 2, 95, 4, 2.5, 60, 4),
            new Product("SUV", "SUV", "Spacious SUVs for up to 6", 6, 125, 3, 31, 2.5, 125, 5, 3, 75, 5));

    public record Account(String name, String phone, String email) {
    }

    public static final Account DEMO_RIDER = new Account("Ananya Rao", "9876500001", "ananya.rao@example.com");
    public static final Account SECOND_RIDER = new Account("Rohan Mehta", "9876500002", "rohan.mehta@example.com");
    public static final Account ADMIN = new Account("Neha Joshi", "9000000000", "admin@ecoride.in");

    /** A driver account with its vehicle. {@code hub} is where a simulated driver lives and cruises. */
    public record DriverSeed(String name, String phone, String type, String make, String model, String color,
                             String plate, int year, String license, double hubLat, double hubLng) {
    }

    public static final DriverSeed DEMO_DRIVER = new DriverSeed("Rahul Patil", "9876600001", "MINI", "Maruti Suzuki",
            "WagonR", "White", "MH12AB4521", 2022, "MH1220190012345", 18.5635, 73.7790);
    public static final DriverSeed PENDING_DRIVER = new DriverSeed("Sandeep Kulkarni", "9876600002", "SEDAN", "Honda",
            "Amaze", "Grey", "MH14CD7788", 2021, "MH1420180098765", 18.5204, 73.8567);
    public static final DriverSeed SUSPENDED_DRIVER = new DriverSeed("Imran Shaikh", "9876600003", "AUTO", "Bajaj",
            "RE Compact", "Green", "MH12RT3390", 2019, "MH1220150044321", 18.5089, 73.8259);

    public static final List<DriverSeed> SIMULATED_DRIVERS = List.of(
            new DriverSeed("Vikram Singh", "9100000001", "MINI", "Hyundai", "Grand i10", "Silver", "MH12QW1101", 2021, "MH1220170000101", 18.5668, 73.7712),
            new DriverSeed("Suresh Yadav", "9100000002", "SEDAN", "Maruti Suzuki", "Dzire", "White", "MH12QW1102", 2022, "MH1220170000102", 18.5562, 73.7898),
            new DriverSeed("Prakash Jadhav", "9100000003", "AUTO", "Bajaj", "RE", "Yellow", "MH12QW1103", 2020, "MH1220170000103", 18.5598, 73.7760),
            new DriverSeed("Amit Verma", "9100000004", "BIKE", "Honda", "Shine", "Black", "MH12QW1104", 2023, "MH1220170000104", 18.5735, 73.7825),
            new DriverSeed("Rakesh Pawar", "9100000005", "MINI", "Tata", "Tiago", "Blue", "MH12QW1105", 2022, "MH1220170000105", 18.5905, 73.7380),
            new DriverSeed("Manoj Gupta", "9100000006", "SUV", "Maruti Suzuki", "Ertiga", "White", "MH12QW1106", 2023, "MH1220170000106", 18.5870, 73.7300),
            new DriverSeed("Deepak More", "9100000007", "SEDAN", "Hyundai", "Aura", "Grey", "MH14QW1107", 2021, "MH1420170000107", 18.6010, 73.7650),
            new DriverSeed("Sachin Shinde", "9100000008", "MINI", "Maruti Suzuki", "Swift", "Red", "MH12QW1108", 2020, "MH1220170000108", 18.5600, 73.8080),
            new DriverSeed("Ganesh Kale", "9100000009", "AUTO", "Piaggio", "Ape City", "Green", "MH12QW1109", 2019, "MH1220170000109", 18.5300, 73.8470),
            new DriverSeed("Nitin Chavan", "9100000010", "BIKE", "TVS", "Jupiter", "White", "MH12QW1110", 2022, "MH1220170000110", 18.5240, 73.8400),
            new DriverSeed("Arjun Nair", "9100000011", "SEDAN", "Toyota", "Etios", "White", "MH12QW1111", 2020, "MH1220170000111", 18.5370, 73.8920),
            new DriverSeed("Faisal Khan", "9100000012", "SUV", "Toyota", "Innova Crysta", "Silver", "MH12QW1112", 2022, "MH1220170000112", 18.5400, 73.8870),
            new DriverSeed("Kunal Deshmukh", "9100000013", "MINI", "Renault", "Kwid", "Orange", "MH12QW1113", 2021, "MH1220170000113", 18.5680, 73.9120),
            new DriverSeed("Harish Reddy", "9100000014", "SEDAN", "Honda", "City", "Black", "MH12QW1114", 2023, "MH1220170000114", 18.5640, 73.9180),
            new DriverSeed("Sanjay Patil", "9100000015", "MINI", "Maruti Suzuki", "Celerio", "Grey", "MH12QW1115", 2022, "MH1220170000115", 18.5510, 73.9420),
            new DriverSeed("Vijay Gaikwad", "9100000016", "AUTO", "Bajaj", "RE", "Yellow", "MH12QW1116", 2021, "MH1220170000116", 18.5030, 73.9330),
            new DriverSeed("Rajesh Kumar", "9100000017", "MINI", "Hyundai", "Santro", "White", "MH12QW1117", 2020, "MH1220170000117", 18.5160, 73.9260),
            new DriverSeed("Mahesh Bhosale", "9100000018", "MINI", "Tata", "Punch", "Blue", "MH12QW1118", 2023, "MH1220170000118", 18.5080, 73.8060),
            new DriverSeed("Anil Sawant", "9100000019", "BIKE", "Bajaj", "Pulsar", "Black", "MH12QW1119", 2022, "MH1220170000119", 18.5110, 73.8120),
            new DriverSeed("Ravi Iyer", "9100000020", "SUV", "Kia", "Carens", "White", "MH12QW1120", 2024, "MH1220170000120", 18.6200, 73.8000),
            new DriverSeed("Yogesh Thorat", "9100000021", "MINI", "Maruti Suzuki", "Alto K10", "Silver", "MH12QW1121", 2023, "MH1220170000121", 18.6220, 73.8040),
            new DriverSeed("Kiran Salunkhe", "9100000022", "SEDAN", "Maruti Suzuki", "Ciaz", "Brown", "MH12QW1122", 2021, "MH1220170000122", 18.5290, 73.8740));

    public static final List<String> SIMULATED_RIDER_NAMES = List.of(
            "Aditi Kulkarni", "Rahul Deshpande", "Sneha Patil", "Karan Malhotra", "Pooja Iyer", "Siddharth Rao",
            "Meera Nair", "Arjun Kapoor", "Isha Joshi", "Varun Sethi", "Neha Gupta", "Aakash Jain", "Divya Menon",
            "Rohit Bhatia", "Shruti Sharma", "Tanvi Desai", "Nikhil Agarwal", "Ritu Saxena", "Harsh Vardhan",
            "Kavya Reddy", "Aniket Pawar", "Priyanka Bose", "Yash Thakur", "Sana Sheikh", "Manish Tiwari",
            "Ayesha Khan", "Gaurav Chaudhary", "Pallavi Shinde", "Omkar Gokhale", "Riya Banerjee", "Kunal Shah",
            "Swati Mishra", "Abhishek Singh", "Nisha Pillai", "Sameer Kulkarni", "Anjali Verma");
}
