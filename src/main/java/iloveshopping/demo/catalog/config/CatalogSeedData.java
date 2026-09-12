package iloveshopping.demo.catalog.config;

import iloveshopping.demo.catalog.entity.Brand;
import iloveshopping.demo.catalog.entity.Category;
import iloveshopping.demo.catalog.entity.Product;
import iloveshopping.demo.catalog.repository.BrandRepository;
import iloveshopping.demo.catalog.repository.CategoryRepository;
import iloveshopping.demo.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;

/** Development catalog so the storefront can be evaluated immediately. */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class CatalogSeedData implements CommandLineRunner {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final BrandRepository brands;

    @Override public void run(String... args) {
        if (products.count() > 0) return;
        Category category = categories.save(Category.builder().name("Everyday finds").slug("everyday-finds").build());
        Brand brand = brands.save(Brand.builder().name("Sample Goods").build());
        List.of(
                product("Canvas Tote Bag", "A durable everyday tote with a roomy main compartment.", "18.00", 30, category, brand),
                product("Ceramic Coffee Mug", "A simple 350 ml mug for coffee, tea, or cocoa.", "12.50", 45, category, brand),
                product("Desk Organizer", "Keeps small office essentials within easy reach.", "24.00", 18, category, brand),
                product("Wireless Lamp", "Warm adjustable light for a bedside table or workspace.", "39.99", 12, category, brand),
                product("Soft Throw Blanket", "Lightweight, comfortable blanket for home or travel.", "31.00", 20, category, brand),
                product("Reusable Water Bottle", "Insulated 600 ml bottle for daily hydration.", "22.00", 50, category, brand)
        ).forEach(products::save);
    }
    private Product product(String name, String description, String price, int stock, Category category, Brand brand) {
        return Product.builder().name(name).description(description).price(new BigDecimal(price)).stockQuantity(stock).averageRating(4.5).reviewCount(10).category(category).brand(brand).build();
    }
}
