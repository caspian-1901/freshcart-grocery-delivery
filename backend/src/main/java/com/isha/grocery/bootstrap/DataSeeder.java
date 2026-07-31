package com.isha.grocery.bootstrap;

import com.isha.grocery.domain.*;
import com.isha.grocery.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Seeds the godown inventory, delivery slots and a demo account. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Time windows offered every day. */
    private static final LocalTime[][] WINDOWS = {
            {LocalTime.of(8, 0), LocalTime.of(10, 0)},
            {LocalTime.of(10, 0), LocalTime.of(12, 0)},
            {LocalTime.of(12, 0), LocalTime.of(14, 0)},
            {LocalTime.of(16, 0), LocalTime.of(18, 0)},
            {LocalTime.of(18, 0), LocalTime.of(20, 0)},
            {LocalTime.of(20, 0), LocalTime.of(22, 0)},
    };

    private final ItemRepository items;
    private final DeliverySlotRepository slots;
    private final UserRepository users;
    private final CartRepository carts;
    private final AddressRepository addresses;
    private final PasswordEncoder encoder;

    public DataSeeder(ItemRepository items, DeliverySlotRepository slots, UserRepository users,
                      CartRepository carts, AddressRepository addresses, PasswordEncoder encoder) {
        this.items = items;
        this.slots = slots;
        this.users = users;
        this.carts = carts;
        this.addresses = addresses;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedItems();
        seedSlots();
        seedDemoUser();
    }

    private void seedItems() {
        if (items.count() > 0) {
            return;
        }
        items.saveAll(List.of(
                item("Tomatoes", "Vegetables", "Fresh local tomatoes", "🍅", "1 kg", "40.00", 60),
                item("Onions", "Vegetables", "Nashik red onions", "🧅", "1 kg", "35.00", 80),
                item("Potatoes", "Vegetables", "Everyday cooking potatoes", "🥔", "1 kg", "30.00", 100),
                item("Spinach", "Vegetables", "Washed and ready to cook", "🥬", "250 g", "25.00", 24),
                item("Carrots", "Vegetables", "Sweet and crunchy", "🥕", "500 g", "32.00", 40),
                item("Bananas", "Fruits", "Robusta bananas", "🍌", "6 pcs", "48.00", 50),
                item("Apples", "Fruits", "Shimla apples", "🍎", "1 kg", "160.00", 30),
                item("Oranges", "Fruits", "Juicy Nagpur oranges", "🍊", "1 kg", "90.00", 25),
                item("Grapes", "Fruits", "Seedless green grapes", "🍇", "500 g", "70.00", 18),
                item("Full Cream Milk", "Dairy", "Pasteurised, 1 litre pack", "🥛", "1 L", "68.00", 45),
                item("Curd", "Dairy", "Set curd, fresh daily", "🍶", "400 g", "45.00", 30),
                item("Paneer", "Dairy", "Soft malai paneer", "🧀", "200 g", "95.00", 20),
                item("Butter", "Dairy", "Salted table butter", "🧈", "100 g", "58.00", 35),
                item("Eggs", "Dairy", "Farm fresh, tray of 6", "🥚", "6 pcs", "42.00", 60),
                item("Basmati Rice", "Staples", "Long grain, aged", "🍚", "1 kg", "135.00", 40),
                item("Whole Wheat Atta", "Staples", "Chakki fresh atta", "🌾", "1 kg", "58.00", 50),
                item("Toor Dal", "Staples", "Unpolished toor dal", "🫘", "1 kg", "165.00", 28),
                item("Sunflower Oil", "Staples", "Refined cooking oil", "🛢️", "1 L", "145.00", 32),
                item("Sugar", "Staples", "Fine grain sugar", "🍬", "1 kg", "48.00", 45),
                item("Brown Bread", "Bakery", "Baked this morning", "🍞", "400 g", "50.00", 22),
                item("Pav Buns", "Bakery", "Soft ladi pav", "🥐", "6 pcs", "30.00", 26),
                item("Tea Leaves", "Beverages", "Assam strong tea", "🍵", "250 g", "150.00", 30),
                item("Instant Coffee", "Beverages", "Freeze dried coffee", "☕", "50 g", "195.00", 15),
                item("Potato Chips", "Snacks", "Salted classic", "🥔", "80 g", "40.00", 55),
                item("Biscuits", "Snacks", "Marie light biscuits", "🍪", "250 g", "45.00", 48),
                item("Dish Wash Gel", "Household", "Lemon dishwash liquid", "🧼", "500 ml", "115.00", 25),
                item("Detergent Powder", "Household", "For machine wash", "🧺", "1 kg", "165.00", 20)
        ));
        log.info("Seeded {} catalog items", items.count());
    }

    private void seedSlots() {
        LocalDate today = LocalDate.now();
        int created = 0;
        // Keep a rolling week of slots available.
        for (int day = 0; day < 7; day++) {
            LocalDate date = today.plusDays(day);
            for (LocalTime[] window : WINDOWS) {
                if (slots.findBySlotDateAndStartTime(date, window[0]).isPresent()) {
                    continue;
                }
                slots.save(DeliverySlot.builder()
                        .slotDate(date)
                        .startTime(window[0])
                        .endTime(window[1])
                        .capacity(5)
                        .booked(0)
                        .build());
                created++;
            }
        }
        if (created > 0) {
            log.info("Seeded {} delivery slots", created);
        }
    }

    private void seedDemoUser() {
        if (users.existsByEmailIgnoreCase("demo@grocery.test")) {
            return;
        }
        User demo = users.save(User.builder()
                .name("Demo User")
                .email("demo@grocery.test")
                .passwordHash(encoder.encode("demo1234"))
                .phone("9876543210")
                .build());

        carts.save(Cart.builder().user(demo).build());
        addresses.save(Address.builder()
                .user(demo)
                .label("Home")
                .line1("12, Green Park Residency")
                .line2("Near City Mall")
                .city("Pune")
                .pincode("411014")
                .phone("9876543210")
                .defaultAddress(true)
                .build());

        log.info("Seeded demo account demo@grocery.test / demo1234");
    }

    private Item item(String name, String category, String description, String emoji,
                      String unit, String price, int quantity) {
        return Item.builder()
                .name(name)
                .category(category)
                .description(description)
                .emoji(emoji)
                .unit(unit)
                .price(new BigDecimal(price))
                .availableQuantity(quantity)
                .active(true)
                .build();
    }
}
