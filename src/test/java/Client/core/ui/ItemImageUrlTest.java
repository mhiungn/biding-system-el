package Client.core.ui;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemImageUrlTest {

    @Test
    void thumbnailAddsCloudinaryDisplayTransformation() {
        String url = "https://res.cloudinary.com/demo/image/upload/v1710000000/auction-items/item/main.png";

        String transformed = invokeImageUrl("thumbnail", url);

        assertEquals(
                "https://res.cloudinary.com/demo/image/upload/c_limit,w_400,q_auto:good,f_auto/v1710000000/auction-items/item/main.png",
                transformed);
    }

    @Test
    void detailAddsLargerCloudinaryDisplayTransformation() {
        String url = "https://res.cloudinary.com/demo/image/upload/v1710000000/auction-items/item/main.jpg";

        String transformed = invokeImageUrl("detail", url);

        assertEquals(
                "https://res.cloudinary.com/demo/image/upload/c_limit,w_900,q_auto:good,f_auto/v1710000000/auction-items/item/main.jpg",
                transformed);
    }

    @Test
    void existingTransformedCloudinaryUrlIsLeftUnchanged() {
        String url = "https://res.cloudinary.com/demo/image/upload/c_limit,w_300/v1710000000/auction-items/item/main.jpg";

        assertEquals(url, invokeImageUrl("thumbnail", url));
    }

    @Test
    void localPathStillConvertsToFileUrlForFallbackCompatibility() {
        String path = "uploads/items/item-001/image.png";

        assertEquals(Path.of(path).toUri().toString(), invokeImageUrl("thumbnail", path));
    }

    private String invokeImageUrl(String methodName, String path) {
        try {
            Class<?> helper = Class.forName("Client.core.ui.ItemImageUrl");
            Method method = helper.getMethod(methodName, String.class);
            return (String) method.invoke(null, path);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot invoke ItemImageUrl." + methodName, e);
        }
    }
}
