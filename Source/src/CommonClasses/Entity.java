package CommonClasses;

import java.io.Serializable;
import java.util.UUID;

public abstract class Entity implements Serializable {
    protected String id;
    public Entity() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
    	return id;
    }
    public abstract String getDisplayInfo();
}