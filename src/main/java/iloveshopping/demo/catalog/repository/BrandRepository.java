package iloveshopping.demo.catalog.repository;

import iloveshopping.demo.catalog.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {}
