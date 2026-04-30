package ch.uzh.ifi.hase.soprafs26.controller;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.uzh.ifi.hase.soprafs26.config.AuthFilter;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.exceptions.GlobalExceptionAdvice;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.service.PantryService;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PantryItemPostDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

@WebMvcTest(PantryController.class)
@Import(GlobalExceptionAdvice.class)
class PantryControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private PantryService pantryService;

        @MockitoBean
        private UserRepository userRepository;

        @MockitoBean
        private AuthFilter authFilter;

        @BeforeEach
        void setUp() throws Exception {
        doAnswer(invocation -> {
                ServletRequest request = invocation.getArgument(0);
                ServletResponse response = invocation.getArgument(1);
                FilterChain chain = invocation.getArgument(2);

                ((HttpServletRequest) request).setAttribute("authenticatedUserId", 99L);
                chain.doFilter(request, response);
                return null;
        }).when(authFilter).doFilter(any(), any(), any());
        }

        @Test
        void consumePantryItem_success_returnsOk() throws Exception {
        PantryService.ConsumeResult result = new PantryService.ConsumeResult();
        result.setItemId(10L);
        result.setRemainingCount(3);
        result.setConsumedCalories(200.0);
        result.setRemoved(false);

        when(pantryService.consumeItem(1L, 10L, 2, 99L)).thenReturn(result);

        String requestBody = """
                {
                        "quantity": 2
                }
                """;

        mockMvc.perform(post("/households/1/pantry/10/consume")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(10))
                .andExpect(jsonPath("$.remainingCount").value(3))
                .andExpect(jsonPath("$.consumedCalories").value(200.0))
                .andExpect(jsonPath("$.removed").value(false));
        }

        @Test
        void consumePantryItem_invalidQuantity_returnsBadRequest() throws Exception {
        when(pantryService.consumeItem(1L, 10L, 0, 99L))
                .thenThrow(new IllegalArgumentException("Quantity must be greater than zero."));

        String requestBody = """
                {
                        "quantity": 0
                }
                """;

        mockMvc.perform(post("/households/1/pantry/10/consume")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quantity must be greater than zero."));
        }

        @Test
        void getPantry_success_returnsItemsAndTotalCalories() throws Exception {
        PantryItem item1 = new PantryItem();
        item1.setId(10L);
        item1.setHouseholdId(1L);
        item1.setBarcode("111");
        item1.setName("Milk");
        item1.setKcalPerPackage(100.0);
        item1.setCount(2);
        item1.setAddedAt(Instant.parse("2026-03-29T10:15:30Z"));

        PantryItem item2 = new PantryItem();
        item2.setId(11L);
        item2.setHouseholdId(1L);
        item2.setBarcode("222");
        item2.setName("Bread");
        item2.setKcalPerPackage(250.0);
        item2.setCount(1);
        item2.setAddedAt(Instant.parse("2026-03-29T11:15:30Z"));

        when(pantryService.getPantryItems(1L, 99L)).thenReturn(List.of(item1, item2));
        when(pantryService.calculateTotalCalories(1L)).thenReturn(450.0);

        mockMvc.perform(get("/households/1/pantry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCalories").value(450.0))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[0].householdId").value(1))
                .andExpect(jsonPath("$.items[0].barcode").value("111"))
                .andExpect(jsonPath("$.items[0].name").value("Milk"))
                .andExpect(jsonPath("$.items[0].kcalPerPackage").value(100.0))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].id").value(11))
                .andExpect(jsonPath("$.items[1].householdId").value(1))
                .andExpect(jsonPath("$.items[1].barcode").value("222"))
                .andExpect(jsonPath("$.items[1].name").value("Bread"))
                .andExpect(jsonPath("$.items[1].kcalPerPackage").value(250.0))
                .andExpect(jsonPath("$.items[1].count").value(1));
        }

        @Test
        void getPantry_whenUserIsNotMember_returnsBadRequest() throws Exception {
        when(pantryService.getPantryItems(1L, 99L))
                .thenThrow(new IllegalArgumentException("User is not a member of this household."));

        mockMvc.perform(get("/households/1/pantry"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User is not a member of this household."));
        }

        @Test
        void addPantryItem_success_returnsCreated() throws Exception {
                PantryItem savedItem = new PantryItem();
                savedItem.setId(10L);
                savedItem.setHouseholdId(1L);
                savedItem.setBarcode("7612345678901");
                savedItem.setName("Milk");
                savedItem.setKcalPerPackage(120.0);
                savedItem.setCount(2);
                savedItem.setAddedAt(Instant.parse("2026-03-29T10:15:30Z"));

                when(pantryService.addItem(
                        eq(1L),
                        any(PantryItemPostDTO.class),
                        eq(99L)
                )).thenReturn(savedItem);

                String requestBody = """
                        {
                                "barcode": "7612345678901",
                                "name": "Milk",
                                "kcalPerPackage": 120.0,
                                "quantity": 2
                        }
                        """;

                mockMvc.perform(post("/households/1/pantry")
                                .contentType("application/json")
                                .content(requestBody))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").value(10))
                        .andExpect(jsonPath("$.householdId").value(1))
                        .andExpect(jsonPath("$.barcode").value("7612345678901"))
                        .andExpect(jsonPath("$.name").value("Milk"))
                        .andExpect(jsonPath("$.kcalPerPackage").value(120.0))
                        .andExpect(jsonPath("$.count").value(2));
        }

        @Test
        void addPantryItem_invalidQuantity_returnsBadRequest() throws Exception {
        when(pantryService.addItem(
                eq(1L),
                any(PantryItemPostDTO.class),
                eq(99L)
        )).thenThrow(new IllegalArgumentException("Quantity must be greater than zero."));

        String requestBody = """
                {
                        "barcode": "7612345678901",
                        "name": "Milk",
                        "kcalPerPackage": 120.0,
                        "quantity": 0
                }
                """;

        mockMvc.perform(post("/households/1/pantry")
                                .contentType("application/json")
                                .content(requestBody))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").value("Quantity must be greater than zero."));
        }

        @Test
        void bulkAddPantryItems_success_returnsOkWithItems() throws Exception {
                PantryItem item1 = new PantryItem();
                item1.setId(10L);
                item1.setHouseholdId(1L);
                item1.setBarcode("111");
                item1.setName("A");
                item1.setKcalPerPackage(100.0);
                item1.setCount(1);
                item1.setAddedAt(Instant.parse("2026-03-29T10:15:30Z"));

                PantryItem item2 = new PantryItem();
                item2.setId(11L);
                item2.setHouseholdId(1L);
                item2.setBarcode("222");
                item2.setName("B");
                item2.setKcalPerPackage(200.0);
                item2.setCount(2);
                item2.setAddedAt(Instant.parse("2026-03-29T11:15:30Z"));

                when(pantryService.bulkAddItems(eq(1L), anyList(), eq(99L))).thenReturn(List.of(item1, item2));

                String requestBody = """
                        {
                                "items": [
                                        {
                                                "barcode": "111",
                                                "name": "A",
                                                "kcalPerPackage": 100.0,
                                                "quantity": 1
                                        },
                                        {
                                                "barcode": "222",
                                                "name": "B",
                                                "kcalPerPackage": 200.0,
                                                "quantity": 2
                                        }
                                ]
                        }
                        """;

                mockMvc.perform(post("/households/1/pantry/bulk-add")
                                .contentType("application/json")
                                .content(requestBody))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(2))
                        .andExpect(jsonPath("$[0].id").value(10))
                        .andExpect(jsonPath("$[0].barcode").value("111"))
                        .andExpect(jsonPath("$[1].id").value(11))
                        .andExpect(jsonPath("$[1].count").value(2));
        }

        @Test
        void bulkAddPantryItems_emptyItems_returnsBadRequest() throws Exception {
                when(pantryService.bulkAddItems(eq(1L), anyList(), eq(99L)))
                        .thenThrow(new IllegalArgumentException("Bulk add payload must contain at least one item."));

                mockMvc.perform(post("/households/1/pantry/bulk-add")
                                .contentType("application/json")
                                .content("{ \"items\": [] }"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").value("Bulk add payload must contain at least one item."));
        }

        @Test
        void removePantryItem_success_returnsOk() throws Exception {
        PantryService.ConsumeResult result = new PantryService.ConsumeResult();
        result.setItemId(10L);
        result.setRemainingCount(2);
        result.setConsumedCalories(0.0);
        result.setRemoved(false);

        when(pantryService.removeItem(1L, 10L, 1, 99L)).thenReturn(result);

        String requestBody = """
                {
                        "quantity": 1
                }
                """;

        mockMvc.perform(post("/households/1/pantry/10/remove")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(10))
                .andExpect(jsonPath("$.remainingCount").value(2))
                .andExpect(jsonPath("$.consumedCalories").value(0.0))
                .andExpect(jsonPath("$.removed").value(false));
        }

        @Test
        void removePantryItem_invalidQuantity_returnsBadRequest() throws Exception {
        when(pantryService.removeItem(1L, 10L, 0, 99L))
                .thenThrow(new IllegalArgumentException("Quantity must be greater than zero."));

        String requestBody = """
                {
                        "quantity": 0
                }
                """;

        mockMvc.perform(post("/households/1/pantry/10/remove")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quantity must be greater than zero."));
        }

}
