package mn.icode.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.icode.entity.Product;
import mn.icode.entity.ProductImage;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.repository.ProductImageRepository;

@Service 
public class ProductImageService {
    
    private final ProductImageRepository imageRepository;
    private final ProductService productService;

    public ProductImageService(ProductImageRepository imageRepository, ProductService productService) {
        this.imageRepository = imageRepository;
        this.productService = productService;
    }

    @Transactional(readOnly=true)
    public List<ProductImage> findByProductId(Long productId) {
        productService.findById(productId);
        
        return imageRepository.findByProductIdOrderByPositionAsc(productId);
    }

    @Transactional
    public ProductImage addImage(Long productId, String imageUrl) {
        Product product = productService.findById(productId);

        List<ProductImage> images = imageRepository.findByProductIdOrderByPositionAsc(productId);

        int nextPosition = images.size();

        ProductImage image = new ProductImage(product, imageUrl, nextPosition);

        return imageRepository.save(image);
    }

    @Transactional 
    public void deleteImage(Long productId, Long imageId) {
        Product product = productService.findById(productId);

        List<ProductImage> images = imageRepository.findByProductIdOrderByPositionAsc(productId);

        ProductImage imageToDelete = images.stream().filter(image -> image.getId().equals(imageId)).findFirst().orElseThrow(()-> new ResourceNotFoundException("Product image not found"));

        int deletedPosition = imageToDelete.getPosition();

        imageRepository.delete(imageToDelete);
        imageRepository.flush();

        int temporaryPosition = images.stream().mapToInt(ProductImage::getPosition).max().orElse(-1) + 1;

        for(ProductImage image : images) {
            if(!image.getId().equals(imageId) && image.getPosition() > deletedPosition) {
                image.setPosition(temporaryPosition++);
                imageRepository.saveAndFlush(image);
            }
        }

        List<ProductImage> remaining = imageRepository.findByProductIdOrderByPositionAsc(productId);

        int position = 0;
        for(ProductImage image : remaining) {
            image.setPosition(position++);
            imageRepository.save(image);
        }
    }


@Transactional
public void reorderImages(Long productId, List<Long> imageIds) {

    // 1. Check that the product exists.
    Product product = productService.findById(productId);

    // 2. Get the product's existing images.
    List<ProductImage> images =
            imageRepository.findByProductIdOrderByPositionAsc(productId);

    // 3. Validate the submitted image IDs.
    if (imageIds == null || imageIds.size() != images.size()) {
        throw new IllegalArgumentException(
                "Image IDs must match the product's existing images");
    }

    Set<Long> requestedIds = new HashSet<>(imageIds);

    if (requestedIds.size() != imageIds.size()
            || !requestedIds.equals(
                    images.stream()
                            .map(ProductImage::getId)
                            .collect(java.util.stream.Collectors.toSet()))) {
        throw new IllegalArgumentException(
                "Invalid image IDs or duplicate IDs");
    }

    // 4. Move all images to temporary, unused positions.
    int temporaryPosition = images.stream()
            .mapToInt(ProductImage::getPosition)
            .max()
            .orElse(-1) + 1;

    for (ProductImage image : images) {
        image.setPosition(temporaryPosition++);
        imageRepository.saveAndFlush(image);
    }

    // 5. Assign the new positions in the requested order.
    for (int i = 0; i < imageIds.size(); i++) {

        Long imageId = imageIds.get(i);

        ProductImage image = images.stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElseThrow();

        image.setPosition(i);
        imageRepository.saveAndFlush(image);
    }
}

}
