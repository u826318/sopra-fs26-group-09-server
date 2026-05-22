package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset.LocalDatasetProductDTO;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetLookupService;
import ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup.LocalDatasetProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocalDatasetLookupController.class)
class LocalDatasetLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocalDatasetLookupService localDatasetLookupService;

    @MockitoBean
    private LocalDatasetProductMapper localDatasetProductMapper;

    @MockitoBean
    private UserRepository userRepository;

    private static final String TEST_TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setToken(TEST_TOKEN);
        user.setStatus(UserStatus.ONLINE);
        given(userRepository.findByToken(TEST_TOKEN)).willReturn(user);
    }

    private LocalDatasetProductDTO buildProductDTO() {
        LocalDatasetProductDTO dto = new LocalDatasetProductDTO();
        dto.setProductIndex(123L);
        dto.setBarcode("0012345678901");
        dto.setName("Test Product");
        dto.setBrand("Test Brand");
        dto.setDataSource("local_dataset");
        return dto;
    }

    @Test
    void lookupByBarcode_200_returnsProduct() throws Exception {
        Map<String, String> row = Map.of("code", "0012345678901", "product_index", "123");
        LocalDatasetProductDTO dto = buildProductDTO();

        given(localDatasetLookupService.findRawRowByBarcode("0012345678901"))
                .willReturn(Optional.of(row));
        given(localDatasetProductMapper.toDto(row)).willReturn(dto);

        mockMvc.perform(get("/local-dataset/products/lookup")
                        .param("barcode", "0012345678901")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode", is("0012345678901")))
                .andExpect(jsonPath("$.name", is("Test Product")));
    }

    @Test
    void lookupByBarcode_404_whenNotFound() throws Exception {
        given(localDatasetLookupService.findRawRowByBarcode("9999999999999"))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/local-dataset/products/lookup")
                        .param("barcode", "9999999999999")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void lookupByProductIndex_200_returnsProduct() throws Exception {
        Map<String, String> row = Map.of("product_index", "123");
        LocalDatasetProductDTO dto = buildProductDTO();

        given(localDatasetLookupService.findRawRowByProductIndex(123L))
                .willReturn(Optional.of(row));
        given(localDatasetProductMapper.toDto(row)).willReturn(dto);

        mockMvc.perform(get("/local-dataset/products/lookup-by-index")
                        .param("productIndex", "123")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productIndex", is(123)));
    }

    @Test
    void lookupByProductIndex_404_whenNotFound() throws Exception {
        given(localDatasetLookupService.findRawRowByProductIndex(999L))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/local-dataset/products/lookup-by-index")
                        .param("productIndex", "999")
                        .header("Authorization", TEST_TOKEN))
                .andExpect(status().isNotFound());
    }
}
