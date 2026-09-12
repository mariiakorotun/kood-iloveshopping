package iloveshopping.demo.catalog.repository;

import iloveshopping.demo.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {}
