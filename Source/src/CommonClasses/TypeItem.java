package CommonClasses;

public class TypeItem {
    public static Item createItem(String type, float price, String name, String desc) {
        switch (type.toUpperCase()) {
            case "ELECTRONICS": return new Electronics(price, name, desc);
            case "ART":         return new Art(price, name, desc);
            case "VEHICLE":     return new Vehicle(price, name, desc);
            default:            return new Item(price, name, desc);
        }
    }
}
class Electronics extends Item {
    public Electronics(float price, String name, String desc) {
        super(price, name, desc);
    }
    @Override
    public String toString() {
        return "[Electronics] " + super.toString();
    }
}
class Art extends Item {
    public Art(float price, String name, String desc) {
        super(price, name, desc);
    }

    @Override
    public String toString() {
        return "[Art] " + super.toString();
    }
}

class Vehicle extends Item {
    public Vehicle(float price, String name, String desc) {
        super(price, name, desc);
    }
    @Override
    public String toString() {
        return "[Vehicle] " + super.toString();
    }
}