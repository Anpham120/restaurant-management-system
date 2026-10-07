package vn.khoibep.rms.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.entity.RecipeLine;

public interface RecipeLineRepository extends JpaRepository<RecipeLine, Long> {

    /** One portion of a dish needs this much of an ingredient. */
    interface Portion {
        Long getMenuItemId();

        Long getInventoryItemId();

        BigDecimal getQuantity();
    }

    /** Every recipe with its ingredients, dish by dish (FR-09.8). */
    @Query("""
            select r from RecipeLine r
            join fetch r.item i
            order by r.menuItem.id, i.name""")
    List<RecipeLine> findAllWithItems();

    /** Without loading the ingredients: they are locked and read fresh afterwards (BR-38). */
    @Query("""
            select r.menuItem.id as menuItemId, r.item.id as inventoryItemId, r.quantity as quantity
            from RecipeLine r
            where r.menuItem.id in :menuItemIds""")
    List<Portion> findPortions(@Param("menuItemIds") Collection<Long> menuItemIds);

    /** Runs at once, so the new lines of a recipe never meet the old ones on ux_recipe_line_item. */
    @Modifying
    @Query("delete from RecipeLine r where r.menuItem.id = :menuItemId")
    void deleteRecipe(@Param("menuItemId") Long menuItemId);
}
