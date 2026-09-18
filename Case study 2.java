import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// DOMAIN ENUM
enum TerminalType {
    BULK_SOLID, CONTAINERS, LIQUIDS
}

// Product interfaces 
interface LoadingEquipment {
    String getName();
    double getRateTonsPerHour();
}

interface StabilityValidator {
    double getMaxImbalancePercent();
    double getMaxDraftMeters();
}

interface ShippingDocument {
    String getDescription();
}

// Concrete products: BULK_SOLID family 
class ConveyorBeltBT3 implements LoadingEquipment {
    public String getName() { return "Conveyor belt BT-3"; }
    public double getRateTonsPerHour() { return 320.0; }
}

class BulkSolidStabilityValidator implements StabilityValidator {
    public double getMaxImbalancePercent() { return 8.0; }
    public double getMaxDraftMeters() { return 1.80; }
}

class BulkSolidShippingDocument implements ShippingDocument {
    public String getDescription() { return "Bill of lading with humidity certificate"; }
}

// Concrete products: CONTAINERS family 
class MobileGantryCraneGP1 implements LoadingEquipment {
    public String getName() { return "Mobile gantry crane GP-1"; }
    public double getRateTonsPerHour() { return 180.0; }
}

class ContainerStabilityValidator implements StabilityValidator {
    public double getMaxImbalancePercent() { return 5.0; }
    public double getMaxDraftMeters() { return 2.10; }
}

class ContainerShippingDocument implements ShippingDocument {
    public String getDescription() { return "Unit list with seal number per container"; }
}

// Concrete products: LIQUIDS family 
class LoadingArmBC2 implements LoadingEquipment {
    public String getName() { return "Loading arm BC-2"; }
    public double getRateTonsPerHour() { return 240.0; }
}

class LiquidStabilityValidator implements StabilityValidator {
    public double getMaxImbalancePercent() { return 3.0; }
    public double getMaxDraftMeters() { return 1.95; }
}

class LiquidShippingDocument implements ShippingDocument {
    public String getDescription() { return "Manifest with chemical compatibility certificate"; }
}

// Abstract Factory
interface TerminalFactory {
    LoadingEquipment createLoadingEquipment();
    StabilityValidator createStabilityValidator();
    ShippingDocument createShippingDocument();

    static TerminalFactory forType(TerminalType type) {
        return switch (type) {
            case BULK_SOLID -> new BulkSolidTerminalFactory();
            case CONTAINERS -> new ContainerTerminalFactory();
            case LIQUIDS -> new LiquidTerminalFactory();
        };
    }
}

class BulkSolidTerminalFactory implements TerminalFactory {
    public LoadingEquipment createLoadingEquipment() { return new ConveyorBeltBT3(); }
    public StabilityValidator createStabilityValidator() { return new BulkSolidStabilityValidator(); }
    public ShippingDocument createShippingDocument() { return new BulkSolidShippingDocument(); }
}

class ContainerTerminalFactory implements TerminalFactory {
    public LoadingEquipment createLoadingEquipment() { return new MobileGantryCraneGP1(); }
    public StabilityValidator createStabilityValidator() { return new ContainerStabilityValidator(); }
    public ShippingDocument createShippingDocument() { return new ContainerShippingDocument(); }
}

class LiquidTerminalFactory implements TerminalFactory {
    public LoadingEquipment createLoadingEquipment() { return new LoadingArmBC2(); }
    public StabilityValidator createStabilityValidator() { return new LiquidStabilityValidator(); }
    public ShippingDocument createShippingDocument() { return new LiquidShippingDocument(); }
}
// CARGO UNITS (products created through the Factory Method hierarchy below)
abstract class CargoUnit {
    protected final String id;
    protected final double weight; // gross weight in tons

    protected CargoUnit(String id, double weight) {
        this.id = id;
        this.weight = weight;
    }

    public String getId() { return id; }
    public double getWeight() { return weight; }

    /** Short human readable description used in the loading sequence report. */
    public abstract String getDescription();
}

class ContainerUnit extends CargoUnit {
    private final int feet;
    private final String seal;

    ContainerUnit(String id, double weight, int feet, String seal) {
        super(id, weight);
        this.feet = feet;
        this.seal = seal;
    }

    public int getFeet() { return feet; }
    public String getSeal() { return seal; }

    @Override
    public String getDescription() {
        return id + " (" + feet + " ft, seal " + seal + ")";
    }
}

class BulkUnit extends CargoUnit {
    private final String product;
    private final double humidityPercent;

    BulkUnit(String product, double weight, double humidityPercent) {
        super(product, weight);
        this.product = product;
        this.humidityPercent = humidityPercent;
    }

    @Override
    public String getDescription() {
        return product + " (lot, humidity " + humidityPercent + "%)";
    }
}

class LiquidUnit extends CargoUnit {
    private final String product;
    private final double density;

    LiquidUnit(String product, double weight, double density) {
        super(product, weight);
        this.product = product;
        this.density = density;
    }

    @Override
    public String getDescription() {
        return product + " (tank, density " + density + " kg/L)";
    }
}

// FACTORY METHOD PATTERN
// Problem it solves: the manifest arrives as plain text lines whose format depends on
// the cargo type (container / bulk / liquid), each with its own parsing rules and its
// own weight formula. ManifestRegistrar defines the *template* algorithm (read line,
// try to build a unit, collect it or reject it, never stop the whole process) while
// deferring the actual object-creation step (createUnit, the factory method) to each
// subclass. This avoids the single 400-line "God method" with many "if" branches that
// the case explicitly warns about.

record RejectedLine(String rawLine, String reason) {}

abstract class ManifestRegistrar {
    private final List<CargoUnit> accepted = new ArrayList<>();
    private final List<RejectedLine> rejected = new ArrayList<>();

    /** Template method: reads every line, never stops on a single bad line. */
    public final void process(List<String> lines) {
        for (String rawLine : lines) {
            if (rawLine == null || rawLine.isBlank()) continue;
            String line = rawLine.trim();
            try {
                CargoUnit unit = createUnit(line);
                accepted.add(unit);
            } catch (IllegalArgumentException e) {
                rejected.add(new RejectedLine(line, e.getMessage()));
            }
        }
    }

    /** Factory Method: each concrete registrar knows how to build its own unit type. */
    protected abstract CargoUnit createUnit(String line);

    public List<CargoUnit> getAccepted() { return accepted; }
    public List<RejectedLine> getRejected() { return rejected; }
}

class ContainerRegistrar extends ManifestRegistrar {
    @Override
    protected CargoUnit createUnit(String line) {
        String[] parts = line.split(";");
        if (parts.length != 5 || !parts[0].equals("CNT")) {
            throw new IllegalArgumentException("does not match the container registrar");
        }
        String id = parts[1].trim();
        int feet;
        try {
            feet = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("feet value is not numeric");
        }
        if (feet != 20 && feet != 40) {
            throw new IllegalArgumentException("invalid feet value, must be 20 or 40");
        }
        double netCargo;
        try {
            netCargo = Double.parseDouble(parts[3].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("net cargo value is not numeric");
        }
        String seal = parts[4].trim();
        double tare = (feet == 20) ? 2.2 : 3.8;
        double weight = netCargo + tare;
        return new ContainerUnit(id, weight, feet, seal);
    }
}

class BulkRegistrar extends ManifestRegistrar {
    @Override
    protected CargoUnit createUnit(String line) {
        String[] parts = line.split(";");
        if (parts.length != 4 || !parts[0].equals("GRA")) {
            throw new IllegalArgumentException("does not match the bulk registrar");
        }
        String product = parts[1].trim();
        double tons;
        try {
            tons = Double.parseDouble(parts[2].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("declared tons value is not numeric");
        }
        double humidity;
        try {
            humidity = Double.parseDouble(parts[3].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("humidity percentage is not numeric");
        }
        double weight = tons * (1 + humidity / 100.0);
        return new BulkUnit(product, weight, humidity);
    }
}

class LiquidRegistrar extends ManifestRegistrar {
    @Override
    protected CargoUnit createUnit(String line) {
        String[] parts = line.split(";");
        if (parts.length != 4 || !parts[0].equals("LIQ")) {
            throw new IllegalArgumentException("does not match the liquid registrar");
        }
        String product = parts[1].trim();
        double liters;
        try {
            liters = Double.parseDouble(parts[2].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("liters value is not numeric");
        }
        double density;
        try {
            density = Double.parseDouble(parts[3].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("density value is not numeric");
        }
        double weight = liters * density / 1000.0;
        return new LiquidUnit(product, weight, density);
    }
}

// HOLD (bodega) - simple aggregate used by the packing algorithm and by the Builder
class Hold {
    private final String name;
    private final double capacity;
    private final List<CargoUnit> units = new ArrayList<>();

    Hold(String name, double capacity) {
        this.name = name;
        this.capacity = capacity;
    }

    public String getName() { return name; }
    public double getCapacity() { return capacity; }
    public List<CargoUnit> getUnits() { return units; }

    public double getOccupiedWeight() {
        double sum = 0;
        for (CargoUnit u : units) sum += u.getWeight();
        return sum;
    }

    public double getRemainingCapacity() { return capacity - getOccupiedWeight(); }

    public double getOccupancyPercent() { return getOccupiedWeight() / capacity * 100.0; }

    public void addUnit(CargoUnit unit) { units.add(unit); }
}

// ALGORITHMIC SERVICE (First Fit Decreasing + balance/draft/time rules from section 5)
class StowagePlanningService {

    public List<Hold> createEmptyHolds() {
        List<Hold> holds = new ArrayList<>();
        holds.add(new Hold("B1", 600.0));
        holds.add(new Hold("B2", 750.0));
        holds.add(new Hold("B3", 750.0));
        holds.add(new Hold("B4", 600.0));
        return holds;
    }

    /** First Fit Decreasing. Returns the units that could not be assigned to any hold. */
    public List<CargoUnit> distributeFirstFitDecreasing(List<Hold> holds, List<CargoUnit> units) {
        List<CargoUnit> sorted = new ArrayList<>(units);
        sorted.sort((a, b) -> Double.compare(b.getWeight(), a.getWeight()));

        List<CargoUnit> unassigned = new ArrayList<>();
        for (CargoUnit unit : sorted) {
            boolean placed = false;
            for (Hold hold : holds) {
                if (hold.getRemainingCapacity() >= unit.getWeight()) {
                    hold.addUnit(unit);
                    placed = true;
                    break;
                }
            }
            if (!placed) unassigned.add(unit);
        }
        return unassigned;
    }

    public double bowWeight(List<Hold> holds) {   // "Proa": B1 + B2
        return holds.get(0).getOccupiedWeight() + holds.get(1).getOccupiedWeight();
    }

    public double sternWeight(List<Hold> holds) { // "Popa": B3 + B4
        return holds.get(2).getOccupiedWeight() + holds.get(3).getOccupiedWeight();
    }

    public double calculateImbalancePercent(List<Hold> holds) {
        double bow = bowWeight(holds);
        double stern = sternWeight(holds);
        double total = bow + stern;
        if (total == 0) return 0.0;
        return Math.abs(bow - stern) / total * 100.0;
    }

    /** Tons that must move from the heavy side to the light side to reach exactly the max allowed imbalance. */
    public double calculateTonsToMove(List<Hold> holds, double maxImbalancePercent) {
        double bow = bowWeight(holds);
        double stern = sternWeight(holds);
        double total = bow + stern;
        double diff = Math.abs(bow - stern);
        double targetDiff = maxImbalancePercent / 100.0 * total;
        return Math.max(diff - targetDiff, 0.0) / 2.0;
    }

    public double calculateDraftMeters(double totalWeight) {
        return 0.55 + (totalWeight / 4200.0);
    }

    public String formatLoadingTime(double totalWeight, double ratePerHour) {
        double hoursDecimal = totalWeight / ratePerHour;
        int hours = (int) hoursDecimal;
        int minutes = (int) Math.round((hoursDecimal - hours) * 60);
        if (minutes == 60) { hours++; minutes = 0; }
        return hours + " h " + minutes + " min";
    }

    /** Hold by hold (B1..B4), heaviest to lightest within each hold, globally numbered. */
    public List<String> generateLoadingSequence(List<Hold> holds) {
        List<String> sequence = new ArrayList<>();
        int counter = 1;
        for (Hold hold : holds) {
            List<CargoUnit> unitsSorted = new ArrayList<>(hold.getUnits());
            unitsSorted.sort((a, b) -> Double.compare(b.getWeight(), a.getWeight()));
            for (CargoUnit unit : unitsSorted) {
                sequence.add(String.format("%d. %s <- %s  %.2f t",
                        counter, hold.getName(), unit.getDescription(), unit.getWeight()));
                counter++;
            }
        }
        return sequence;
    }
}

// BUILDER PATTERN
// Problem it solves: a StowagePlan has several mandatory fields and several optional
// ones. The Builder assembles the plan step by step through a fluent API and performs
// all cross-field validation exactly once, inside build(), producing an *immutable*
// StowagePlan object (no setters, no partially-constructed plan can ever leave the
// builder).
final class StowagePlan {
    // Mandatory fields
    private final String planNumber;
    private final LocalDate departureDate;
    private final String bargeId;
    private final TerminalType terminalType;
    private final List<Hold> holds;
    private final double totalWeight;

    // Optional fields
    private final List<String> loadingSequence;
    private final String timeWindow;
    private final String assignedTugboat;
    private final String safetyNotes;
    private final Double declaredDraftRestriction;
    private final String plannerName;

    private StowagePlan(Builder b) {
        this.planNumber = b.planNumber;
        this.departureDate = b.departureDate;
        this.bargeId = b.bargeId;
        this.terminalType = b.terminalType;
        this.holds = List.copyOf(b.holds);
        this.totalWeight = b.totalWeight;
        this.loadingSequence = List.copyOf(b.loadingSequence);
        this.timeWindow = b.timeWindow;
        this.assignedTugboat = b.assignedTugboat;
        this.safetyNotes = b.safetyNotes;
        this.declaredDraftRestriction = b.declaredDraftRestriction;
        this.plannerName = b.plannerName;
    }

    public String getPlanNumber() { return planNumber; }
    public LocalDate getDepartureDate() { return departureDate; }
    public String getBargeId() { return bargeId; }
    public TerminalType getTerminalType() { return terminalType; }
    public List<Hold> getHolds() { return holds; }
    public double getTotalWeight() { return totalWeight; }
    public List<String> getLoadingSequence() { return loadingSequence; }
    public String getTimeWindow() { return timeWindow; }
    public String getAssignedTugboat() { return assignedTugboat; }
    public String getSafetyNotes() { return safetyNotes; }
    public Double getDeclaredDraftRestriction() { return declaredDraftRestriction; }
    public String getPlannerName() { return plannerName; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String planNumber;
        private LocalDate departureDate;
        private String bargeId;
        private TerminalType terminalType;
        private List<Hold> holds;
        private Double totalWeight;

        private List<String> loadingSequence = new ArrayList<>();
        private String timeWindow;
        private String assignedTugboat;
        private String safetyNotes;
        private Double declaredDraftRestriction;
        private String plannerName;

        public Builder planNumber(String planNumber) { this.planNumber = planNumber; return this; }
        public Builder departureDate(LocalDate departureDate) { this.departureDate = departureDate; return this; }
        public Builder bargeId(String bargeId) { this.bargeId = bargeId; return this; }
        public Builder terminalType(TerminalType terminalType) { this.terminalType = terminalType; return this; }
        public Builder holds(List<Hold> holds) { this.holds = holds; return this; }
        public Builder totalWeight(double totalWeight) { this.totalWeight = totalWeight; return this; }

        public Builder loadingSequence(List<String> loadingSequence) { this.loadingSequence = loadingSequence; return this; }
        public Builder timeWindow(String timeWindow) { this.timeWindow = timeWindow; return this; }
        public Builder assignedTugboat(String assignedTugboat) { this.assignedTugboat = assignedTugboat; return this; }
        public Builder safetyNotes(String safetyNotes) { this.safetyNotes = safetyNotes; return this; }
        public Builder declaredDraftRestriction(double declaredDraftRestriction) { this.declaredDraftRestriction = declaredDraftRestriction; return this; }
        public Builder plannerName(String plannerName) { this.plannerName = plannerName; return this; }

        public StowagePlan build() {
            if (planNumber == null || planNumber.isBlank())
                throw new IllegalStateException("Plan number is required");
            if (departureDate == null)
                throw new IllegalStateException("Departure date is required");
            if (bargeId == null || bargeId.isBlank())
                throw new IllegalStateException("Barge registration (bargeId) is required");
            if (terminalType == null)
                throw new IllegalStateException("Terminal type is required");
            if (holds == null || holds.isEmpty())
                throw new IllegalStateException("Hold distribution is required");
            if (totalWeight == null)
                throw new IllegalStateException("Total weight is required");
            if (departureDate.isBefore(LocalDate.now()))
                throw new IllegalStateException("Departure date cannot be before the current system date");

            boolean hasAnyUnit = holds.stream().anyMatch(h -> !h.getUnits().isEmpty());
            if (!hasAnyUnit)
                throw new IllegalStateException("The plan must have at least one cargo unit assigned");

            return new StowagePlan(this);
        }
    }
}

// MAIN APPLICATION (console demo program, section 6 of the case)
public class StowagePlanningApp {

    private static final StowagePlanningService SERVICE = new StowagePlanningService();

    public static void main(String[] args) {
        System.out.println("=== BARRANCABERMEJA RIVER TERMINAL ===");
        System.out.println();

        // Task 1 & 2: container manifest, full plan, print it 
        runContainerDemo();

        System.out.println();
        System.out.println("--------------------------------------------------------------------------");
        System.out.println();

        // Task 3: bulk solid manifest that triggers a NOT APPROVED (imbalance) plan 
        runBulkImbalanceDemo();

        System.out.println();
        System.out.println("--------------------------------------------------------------------------");
        System.out.println();

        // Task 4: builder validation failure 
        runBuilderValidationDemo();
    }

    private static void runContainerDemo() {
        List<String> manifestLines = List.of(
                "CNT;MSKU1234567;40;371.2;SELLO-8891",
                "CNT;TCLU8877213;40;366.2;SELLO-7742",
                "CNT;MSCU4455667;40;296.2;SELLO-1234",
                "CNT;CMAU9988771;40;291.2;SELLO-5566",
                "CNT;OOLU3322114;40;276.2;SELLO-9981",
                "CNT;HLXU7766552;40;271.2;SELLO-4432",
                "CNT;MAEU1122334;40;246.2;SELLO-6673",
                "CNT;TGHU5544332;40;241.2;SELLO-8820",
                "CNT;FCIU9900112;40;236.2;SELLO-3321",
                "CNT;NYKU6677889;40;46.2;SELLO-1190",
                "GRA;CARBON;850;13.5",
                "CNT;MSKU9998887;XX;18.5;SELLO-0001"
        );

        ManifestRegistrar registrar = new ContainerRegistrar();
        registrar.process(manifestLines);

        System.out.printf("Registrar: CONTAINERS | lines read: %d | accepted: %d | rejected: %d%n",
                manifestLines.size(), registrar.getAccepted().size(), registrar.getRejected().size());
        for (RejectedLine r : registrar.getRejected()) {
            System.out.printf("[REJECTED] %s -> %s%n", r.rawLine(), r.reason());
        }
        System.out.println();

        TerminalFactory factory = TerminalFactory.forType(TerminalType.CONTAINERS);
        List<Hold> holds = SERVICE.createEmptyHolds();
        List<CargoUnit> unassigned = SERVICE.distributeFirstFitDecreasing(holds, registrar.getAccepted());
        double totalWeight = holds.stream().mapToDouble(Hold::getOccupiedWeight).sum();

        StowagePlan plan = StowagePlan.builder()
                .planNumber("PE-2026-0148")
                .departureDate(LocalDate.now().plusDays(20))
                .bargeId("BZ-4417")
                .terminalType(TerminalType.CONTAINERS)
                .holds(holds)
                .totalWeight(totalWeight)
                .loadingSequence(SERVICE.generateLoadingSequence(holds))
                .assignedTugboat("REMOLCADOR-RIO-3")
                .plannerName("J. Perez")
                .build();

        printPlan(plan, factory, unassigned);
    }

    private static void runBulkImbalanceDemo() {
        List<String> manifestLines = List.of(
                "GRA;CARBON-TERMICO;500;5",
                "GRA;MINERAL-HIERRO;480;4",
                "GRA;CAL-VIVA;300;6",
                "GRA;FOSFATO-ROCA;200;3",
                "GRA;SAL-INDUSTRIAL;150;2"
        );

        ManifestRegistrar registrar = new BulkRegistrar();
        registrar.process(manifestLines);

        System.out.printf("Registrar: BULK_SOLID | lines read: %d | accepted: %d | rejected: %d%n",
                manifestLines.size(), registrar.getAccepted().size(), registrar.getRejected().size());
        for (RejectedLine r : registrar.getRejected()) {
            System.out.printf("[REJECTED] %s -> %s%n", r.rawLine(), r.reason());
        }
        System.out.println();

        TerminalFactory factory = TerminalFactory.forType(TerminalType.BULK_SOLID);
        List<Hold> holds = SERVICE.createEmptyHolds();
        List<CargoUnit> unassigned = SERVICE.distributeFirstFitDecreasing(holds, registrar.getAccepted());
        double totalWeight = holds.stream().mapToDouble(Hold::getOccupiedWeight).sum();

        StowagePlan plan = StowagePlan.builder()
                .planNumber("PE-2026-0149")
                .departureDate(LocalDate.now().plusDays(10))
                .bargeId("BZ-2201")
                .terminalType(TerminalType.BULK_SOLID)
                .holds(holds)
                .totalWeight(totalWeight)
                .loadingSequence(SERVICE.generateLoadingSequence(holds))
                .safetyNotes("Watch for dust emission during loading")
                .build();

        printPlan(plan, factory, unassigned);
    }

    private static void runBuilderValidationDemo() {
        System.out.println("Attempting to build a plan WITHOUT a barge registration...");
        try {
            List<Hold> holds = SERVICE.createEmptyHolds();
            holds.get(0).addUnit(new BulkUnit("TEST-PRODUCT", 100.0, 0.0));

            StowagePlan.builder()
                    .planNumber("PE-2026-0150")
                    .departureDate(LocalDate.now().plusDays(5))
                    // .bargeId(...) intentionally omitted
                    .terminalType(TerminalType.BULK_SOLID)
                    .holds(holds)
                    .totalWeight(100.0)
                    .build();

            System.out.println("This line should never be reached.");
        } catch (IllegalStateException e) {
            System.out.println("Build rejected as expected -> " + e.getMessage());
        }
    }

    private static void printPlan(StowagePlan plan, TerminalFactory factory, List<CargoUnit> unassigned) {
        LoadingEquipment equipment = factory.createLoadingEquipment();
        StabilityValidator validator = factory.createStabilityValidator();
        ShippingDocument document = factory.createShippingDocument();

        List<Hold> holds = plan.getHolds();
        double totalWeight = plan.getTotalWeight();

        System.out.printf("STOWAGE PLAN No. %s%n", plan.getPlanNumber());
        System.out.printf("Barge: %s | Departure: %s | Terminal: %s%n",
                plan.getBargeId(), plan.getDepartureDate(), plan.getTerminalType());
        System.out.printf("Equipment: %s (%.1f t/h)%n", equipment.getName(), equipment.getRateTonsPerHour());
        System.out.println();

        System.out.printf("%-4s %10s %10s %10s %8s%n", "HOLD", "CARGO(t)", "CAPACITY", "OCCUPANCY", "UNITS");
        for (Hold h : holds) {
            System.out.printf("%-4s %10.2f %10.1f %9.1f%% %8d%n",
                    h.getName(), h.getOccupiedWeight(), h.getCapacity(), h.getOccupancyPercent(), h.getUnits().size());
        }
        System.out.printf("%-4s %10.2f%n", "TOTAL", totalWeight);
        System.out.println();

        double bow = SERVICE.bowWeight(holds);
        double stern = SERVICE.sternWeight(holds);
        double imbalance = SERVICE.calculateImbalancePercent(holds);
        boolean imbalanceOk = imbalance <= validator.getMaxImbalancePercent();

        System.out.printf("Bow (proa): %.2f t | Stern (popa): %.2f t | Imbalance: %.2f%% (max %.2f%%) -> %s%n",
                bow, stern, imbalance, validator.getMaxImbalancePercent(), imbalanceOk ? "OK" : "FAIL");
        if (!imbalanceOk) {
            double tonsToMove = SERVICE.calculateTonsToMove(holds, validator.getMaxImbalancePercent());
            System.out.printf("  -> %.2f t must be moved from the heavy side to the light side to reach the limit%n", tonsToMove);
        }

        double draft = SERVICE.calculateDraftMeters(totalWeight);
        boolean draftOk = draft <= validator.getMaxDraftMeters();
        System.out.printf("Estimated draft: %.2f m (max %.2f m) -> %s%n",
                draft, validator.getMaxDraftMeters(), draftOk ? "OK" : "FAIL");

        String loadingTime = SERVICE.formatLoadingTime(totalWeight, equipment.getRateTonsPerHour());
        System.out.printf("Estimated loading time: %s%n", loadingTime);

        boolean approved = imbalanceOk && draftOk;
        System.out.println();
        System.out.println("PLAN STATUS: " + (approved ? "APPROVED" : "NOT APPROVED"));
        System.out.println();

        System.out.println("LOADING SEQUENCE");
        for (String line : plan.getLoadingSequence()) {
            System.out.println(line);
        }

        if (!unassigned.isEmpty()) {
            System.out.println();
            System.out.println("UNSHIPPED CARGO (did not fit in any hold)");
            for (CargoUnit u : unassigned) {
                System.out.printf("- %s  %.2f t%n", u.getDescription(), u.getWeight());
            }
        }

        System.out.println();
        System.out.println("DOCUMENT: " + document.getDescription());
    }
}
