package Server.service;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SellItemServiceTest {

    @Test
    void listItemRejectsMissingMainPictureBeforeCreatingAuction() throws ReflectiveOperationException {
        Object service = createSellItemService();
        Object request = validRequestWithoutMainImage();
        Method listItem = service.getClass().getMethod("listItem", request.getClass());

        InvocationTargetException error = assertThrows(
                InvocationTargetException.class,
                () -> listItem.invoke(service, request));

        assertEquals("Please select a main picture for this item.", error.getCause().getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectGalleryImagesForSaveKeepsOnlyThreeUniqueAdditionalImages() throws Exception {
        Object service = createSellItemService();
        File main = tempImage("main", ".jpg");
        File first = tempImage("first", ".jpg");
        File second = tempImage("second", ".png");
        File third = tempImage("third", ".jpeg");
        File fourth = tempImage("fourth", ".jpg");

        Object request = validRequestWithoutMainImage();
        invoke(request, "setMainImage", File.class, main);
        List<File> galleryImages = (List<File>) request.getClass().getMethod("getGalleryImages").invoke(request);
        galleryImages.add(main);
        galleryImages.add(first);
        galleryImages.add(first);
        galleryImages.add(second);
        galleryImages.add(third);
        galleryImages.add(fourth);

        Method method = service.getClass().getDeclaredMethod("selectGalleryImagesForSave", request.getClass());
        method.setAccessible(true);
        List<File> selected = (List<File>) method.invoke(service, request);

        assertEquals(List.of(first, second, third), selected);
    }

    private Object validRequestWithoutMainImage() throws ReflectiveOperationException {
        Object request = Class.forName("Client.features.sell.SellItemRequest")
                .getDeclaredConstructor()
                .newInstance();
        invoke(request, "setSellerUsername", String.class, "seller");
        invoke(request, "setItemName", String.class, "Camera");
        invoke(request, "setCategory", String.class, "ELECTRONICS");
        invoke(request, "setCondition", String.class, "Good");
        invoke(request, "setStartingPrice", float.class, 100_000f);
        invoke(request, "setDescription", String.class, "Working camera with original strap.");
        invoke(request, "setLocation", String.class, "Hanoi");
        invoke(request, "setAuctionEndTime", Date.class, new Date(System.currentTimeMillis() + 86_400_000));
        invoke(request, "setMinimumBidIncrement", float.class, 10_000f);
        return request;
    }

    private Object createSellItemService() throws ReflectiveOperationException {
        return Class.forName("Client.features.sell.SellItemService")
                .getDeclaredConstructor()
                .newInstance();
    }

    private void invoke(Object target, String methodName, Class<?> parameterType, Object value)
            throws ReflectiveOperationException {
        target.getClass().getMethod(methodName, parameterType).invoke(target, value);
    }

    private File tempImage(String prefix, String suffix) throws IOException {
        File file = Files.createTempFile(prefix, suffix).toFile();
        file.deleteOnExit();
        return file;
    }
}
