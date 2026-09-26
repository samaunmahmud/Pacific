package com.pacific.marketplace.demo;

import java.util.List;

/**
 * The raw material for demo data: categories, fictional brands and stores, and product types. Everything here is
 * invented (no real brands), so demo listings never imitate a real company's products.
 *
 * <p>{@code art} names one of the drawn icons in the storefront (see frontend DemoArt.tsx).
 */
final class DemoCatalog {

    private DemoCatalog() {
    }

    /** One kind of product. {@code features} are '|'-separated bullet points. */
    record Type(String label, String art, int minPrice, int maxPrice, String features) {
    }

    record Category(String name, String store, List<String> brands, List<String> series, List<Type> types) {
    }

    record Store(String name, String description) {
    }

    private static Type t(String label, String art, int min, int max, String features) {
        return new Type(label, art, min, max, features);
    }

    static final List<Store> STORES = List.of(
            new Store("Halcyon Audio Co.", "Sound gear for people who care how things sound."),
            new Store("Bytewise Tech", "Computer accessories, cables and components, tested before they ship."),
            new Store("Hearthside Home", "Everyday kitchen and home essentials."),
            new Store("Trailhead Outfitters", "Kit for the trail, the gym and everything between."),
            new Store("Pageturner Books", "New releases and quiet favourites."),
            new Store("Loom & Thread", "Comfortable clothing and footwear."),
            new Store("Brightside Toys", "Games and toys for all ages."),
            new Store("Glow Lab", "Beauty and personal care that keeps it simple."));

    /** More stores for the larger catalogue, each selling in the categories listed (all of them when empty). */
    record ExtraStore(String name, String description, List<String> categories) {
    }

    static final List<ExtraStore> EXTRA_STORES = List.of(
            new ExtraStore("Northwind Sound", "Headphones, speakers and turntables, chosen by ear.", List.of("Audio")),
            new ExtraStore("Circuit Yard", "Parts, cables and accessories for people who build their own.",
                    List.of("Computer Accessories", "PC Components", "Cables & Power")),
            new ExtraStore("Deskcraft", "Everything for a desk you enjoy sitting at.", List.of("Home Office", "Computer Accessories")),
            new ExtraStore("Lensmith", "Cameras, lenses and lights for makers of all kinds.", List.of("Photo & Video", "Audio")),
            new ExtraStore("Kettle & Crumb", "Kitchen kit for everyday cooks.", List.of("Home & Kitchen")),
            new ExtraStore("Wildfern Outdoors", "Gear for long walks, early swims and everything outside.",
                    List.of("Sports & Outdoors", "Lifestyle")),
            new ExtraStore("Chapter & Verse", "An independent bookshop with a long reading list.", List.of("Books")),
            new ExtraStore("Thread Theory", "Wardrobe basics that last.", List.of("Fashion")),
            new ExtraStore("Playhouse Corner", "Toys, puzzles and games for rainy days.", List.of("Toys & Games")),
            new ExtraStore("Pure Botanica", "Gentle skincare and grooming.", List.of("Beauty & Health")),
            new ExtraStore("Everyday Essentials Co.", "A bit of everything, sensibly priced.", List.of()),
            new ExtraStore("Harbour Street Market", "Useful things from small makers.", List.of()));

    /** Editions that make otherwise similar product names distinct ("Plus", "Mini"...); blank means none. */
    static final List<String> EDITIONS = List.of("", "", "", "Plus", "Mini", "Max", "Air", "Edge", "Neo", "Pro", "Go", "Lite");

    static final List<String> TITLE_ADJECTIVES = List.of("Quiet", "Hidden", "Last", "Silver", "Winter", "Distant", "Broken",
            "Golden", "Secret", "Wild", "Northern", "Paper", "Midnight", "Little", "Lost", "Salt", "Glass", "Summer",
            "Forgotten", "Restless", "Crooked", "Bright", "Hollow", "Sleeping");
    static final List<String> TITLE_NOUNS = List.of("Harbour", "Orchard", "Lighthouse", "Garden", "River", "Letters",
            "Kingdom", "Hours", "Island", "Map", "Station", "Year", "Tide", "Mountain", "House", "Fox", "Library",
            "Promise", "Road", "Season", "Choir", "Bridge", "Keeper", "Sky");

    static final List<String> FIRST_NAMES = List.of("Amelia", "Oliver", "Isla", "Noah", "Ava", "Arthur", "Freya", "Leo",
            "Mia", "Harry", "Grace", "Jack", "Ivy", "Oscar", "Ruby", "Theo", "Ella", "Finn", "Lily", "George", "Sofia",
            "Charlie", "Poppy", "Henry", "Evie", "Jacob", "Daisy", "Alfie", "Rosie", "Freddie");
    static final List<String> LAST_NAMES = List.of("Bennett", "Clarke", "Davies", "Evans", "Foster", "Grant", "Hughes",
            "Irving", "Jones", "Kaur", "Lewis", "Morgan", "Nash", "Owens", "Patel", "Quinn", "Reid", "Singh", "Turner",
            "Ward", "Young", "Ahmed", "Brooks", "Cole", "Dixon");

    static final List<Category> CATEGORIES = List.of(
            new Category("Audio", "Halcyon Audio Co.",
                    List.of("Halcyon", "Resonic", "Wavecrest", "Auralis"), List.of("Aero", "Pulse", "Studio", "Nova"),
                    List.of(
                            t("Wireless Over-Ear Headphones", "headphones", 45, 190, "Up to 40 hours of playback|Active noise cancelling|Soft memory-foam ear cushions"),
                            t("Gaming Headset with Microphone", "headphones", 30, 110, "Detachable noise-cancelling microphone|Surround-sound drivers|Adjustable padded headband"),
                            t("True Wireless Earbuds", "earbuds", 25, 130, "Charging case adds 24 extra hours|Water and sweat resistant|Touch controls"),
                            t("Sport Earbuds", "earbuds", 20, 70, "Secure ear hooks that stay put|Sweat-proof for workouts|Fast charge: 10 minutes gives 2 hours"),
                            t("Portable Bluetooth Speaker", "speaker", 25, 120, "360-degree room-filling sound|Waterproof for the pool or beach|12 hours of battery"),
                            t("Smart Home Speaker", "speaker", 40, 150, "Built-in voice assistant|Stereo pair support|Wi-Fi and Bluetooth"),
                            t("Soundbar with Wireless Subwoofer", "speaker", 90, 320, "Deep, rich bass|HDMI ARC and optical inputs|Movie, music and dialogue modes"),
                            t("USB Condenser Microphone", "microphone", 35, 140, "Plug and play, no drivers needed|Cardioid pattern reduces background noise|Zero-latency headphone monitoring"),
                            t("Podcasting Microphone Kit", "microphone", 60, 180, "Boom arm and pop filter included|Studio-quality vocals|Mute button with LED"),
                            t("Vinyl Record Player", "speaker", 70, 220, "Belt-drive turntable|Built-in preamp|Bluetooth output to any speaker"))),
            new Category("Computer Accessories", "Bytewise Tech",
                    List.of("Bytewise", "Keystone", "Clickr", "Deskly"), List.of("Pro", "Slim", "Flow", "Core"),
                    List.of(
                            t("Mechanical Gaming Keyboard", "keyboard", 40, 140, "Hot-swappable mechanical switches|Per-key RGB backlight|Braided detachable cable"),
                            t("Wireless Slim Keyboard", "keyboard", 20, 65, "Quiet, low-profile keys|Connects to three devices|Rechargeable, months per charge"),
                            t("Ergonomic Split Keyboard", "keyboard", 45, 130, "Split layout reduces wrist strain|Cushioned palm rest|Full-size number pad"),
                            t("Wireless Optical Mouse", "mouse", 10, 35, "Precise 1600 DPI sensor|Silent clicks|One AA battery lasts a year"),
                            t("Ergonomic Vertical Mouse", "mouse", 20, 60, "Natural handshake position|Adjustable DPI|Rechargeable battery"),
                            t("Gaming Mouse with Programmable Buttons", "mouse", 25, 90, "Up to 16,000 DPI|8 programmable buttons|Lightweight honeycomb shell"),
                            t("1080p HD Webcam", "webcam", 25, 80, "Auto light correction|Dual noise-reducing microphones|Privacy shutter"),
                            t("24-inch Full HD Monitor", "monitor", 90, 160, "IPS panel with wide viewing angles|75Hz refresh rate|Eye-care flicker-free backlight"),
                            t("27-inch 4K Monitor", "monitor", 220, 420, "Sharp 4K UHD resolution|99% sRGB colour coverage|USB-C connection with 65W charging"),
                            t("Curved Ultrawide Monitor", "monitor", 240, 480, "34-inch curved display|Two windows side by side|144Hz for smooth motion"),
                            t("Aluminium Laptop Stand", "laptop", 15, 45, "Raises your screen to eye level|Folds flat for travel|Fits 10 to 17 inch laptops"),
                            t("7-in-1 USB-C Hub", "plug", 18, 60, "HDMI 4K, USB 3.0 and SD card reader|100W power pass-through|Aluminium body that stays cool"))),
            new Category("Cables & Power", "Bytewise Tech",
                    List.of("Voltix", "Linkwell", "Ampere & Co", "Joule"), List.of("Max", "Lite", "Turbo", "Prime"),
                    List.of(
                            t("USB-C to USB-C Cable (2m)", "cable", 6, 18, "Charges laptops up to 100W|Braided nylon that resists fraying|10Gbps data transfer"),
                            t("High-Speed HDMI 2.1 Cable (3m)", "cable", 8, 25, "8K at 60Hz and 4K at 120Hz|Gold-plated connectors|Works with consoles and TVs"),
                            t("Cat 8 Ethernet Cable (10m)", "cable", 9, 24, "Up to 40Gbps speeds|Shielded against interference|Flat, easy to route"),
                            t("3-in-1 Charging Cable", "cable", 7, 20, "USB-C, micro-USB and Lightning tips|1.2m braided design|Charges three devices at once"),
                            t("65W GaN Fast Charger", "charger", 18, 45, "Charges a laptop and a phone together|Compact folding plug|Overheat and surge protection"),
                            t("20W USB-C Wall Charger", "charger", 8, 20, "Fast charging for phones and tablets|Tiny, travel-friendly size|Safety-certified"),
                            t("Wireless Charging Pad", "charger", 12, 35, "15W fast wireless charging|Works through most cases|LED indicator"),
                            t("10,000mAh Power Bank", "battery", 15, 40, "Charges a phone up to 2.5 times|Two USB outputs|Pocket-sized"),
                            t("20,000mAh Power Bank with Display", "battery", 25, 60, "Digital battery level display|65W laptop-ready output|Airline-approved capacity"),
                            t("6-Socket Surge Protector", "plug", 12, 35, "Protects against power spikes|2m cable|Individual switches"),
                            t("Universal Travel Adapter", "plug", 10, 30, "Works in over 150 countries|Four USB ports|Built-in fuse"),
                            t("Smart Wi-Fi Plug (2-pack)", "plug", 12, 30, "Control from your phone|Schedules and timers|Works with popular voice assistants"))),
            new Category("PC Components", "Bytewise Tech",
                    List.of("Corvid", "Ironleaf", "Bitforge", "Quantix"), List.of("X", "Ultra", "Elite", "Fusion"),
                    List.of(
                            t("1TB NVMe SSD", "chip", 45, 110, "Read speeds up to 3,500MB/s|Five-year warranty|Slim M.2 design"),
                            t("2TB SATA SSD", "chip", 80, 170, "Boots and loads in seconds|Shock-resistant|Works with laptops and desktops"),
                            t("16GB DDR4 RAM Kit (2 x 8GB)", "chip", 30, 75, "3200MHz for smooth multitasking|Heat-spreader design|Lifetime warranty"),
                            t("32GB DDR5 RAM Kit", "chip", 80, 170, "6000MHz high-speed memory|XMP profiles for easy setup|Low-profile heat spreaders"),
                            t("128GB microSD Card", "chip", 10, 28, "Reads up to 190MB/s|Suits phones, cameras and handhelds|Waterproof and shockproof"),
                            t("Tower CPU Air Cooler", "fan", 25, 70, "Six copper heat pipes|Quiet 120mm PWM fan|Fits most modern sockets"),
                            t("240mm Liquid CPU Cooler", "fan", 60, 130, "Low-noise pump|Addressable RGB lighting|Two 120mm radiator fans"),
                            t("120mm Case Fans (3-pack)", "fan", 12, 32, "Whisper-quiet bearings|Anti-vibration pads|RGB hub included"),
                            t("750W Modular Power Supply", "battery", 65, 130, "80 Plus Gold efficiency|Fully modular cables|Ten-year warranty"),
                            t("ATX Motherboard", "chip", 110, 260, "Latest-generation socket support|Wi-Fi 6 and Bluetooth|Four M.2 slots"),
                            t("Mid-Tower PC Case", "monitor", 45, 110, "Tempered-glass side panel|Excellent airflow mesh front|Cable management channels"),
                            t("Graphics Card 8GB", "fan", 180, 420, "Ray tracing and 4K gaming|Triple-fan cooling|Three DisplayPort outputs"))),
            new Category("Home Office", "Bytewise Tech",
                    List.of("Deskly", "Nordhaven", "Ergovia", "Workwell"), List.of("One", "Plus", "Form", "Air"),
                    List.of(
                            t("Ergonomic Mesh Office Chair", "chair", 95, 260, "Adjustable lumbar support|Breathable mesh back|Height and tilt adjustment"),
                            t("Executive Leather Chair", "chair", 130, 320, "Padded armrests|Smooth-rolling castors|Supports up to 150kg"),
                            t("Kneeling Posture Chair", "chair", 60, 150, "Encourages an upright spine|Solid wood frame|Compact and easy to store"),
                            t("Electric Standing Desk", "desk", 180, 480, "Whisper-quiet height adjustment|Memory presets|Holds up to 80kg"),
                            t("Compact Writing Desk", "desk", 60, 150, "Sturdy steel frame|Cable management hole|Simple tool-free assembly"),
                            t("Monitor Riser with Storage", "desk", 20, 55, "Lifts your screen to eye level|Drawer for pens and notes|Bamboo finish"),
                            t("LED Desk Lamp", "lamp", 18, 55, "Five brightness levels, three colour tones|Flexible neck|USB charging port"),
                            t("Architect Clamp Lamp", "lamp", 22, 60, "Clamps to any desk edge|Eye-care LED|Touch dimmer"),
                            t("Under-Desk Footrest", "chair", 15, 40, "Adjustable angle|Massage-textured surface|Non-slip base"),
                            t("A5 Hardback Notebook (3-pack)", "book", 8, 22, "Thick, ink-friendly paper|Elastic closure and ribbon|Lay-flat binding"),
                            t("Whiteboard with Stand", "monitor", 30, 90, "Magnetic dry-erase surface|Adjustable height|Pen tray included"),
                            t("Desk Organiser Set", "desk", 10, 30, "Six-piece set|Keeps pens, phone and notes tidy|Durable metal mesh"))),
            new Category("Photo & Video", "Bytewise Tech",
                    List.of("Lumora", "Framecraft", "Optix", "Snapwell"), List.of("Vista", "Zoom", "Pro", "Go"),
                    List.of(
                            t("Mirrorless Camera with 18-55mm Lens", "camera", 480, 920, "24MP APS-C sensor|4K video recording|Built-in Wi-Fi for easy sharing"),
                            t("Compact Point-and-Shoot Camera", "camera", 140, 340, "20x optical zoom|Pocket-sized|Image stabilisation"),
                            t("4K Action Camera", "camera", 80, 260, "Waterproof to 10 metres|Steady video stabilisation|Mounts included"),
                            t("50mm f/1.8 Prime Lens", "camera", 90, 210, "Beautiful background blur|Fast, quiet autofocus|Lightweight and sharp"),
                            t("Aluminium Travel Tripod", "tripod", 30, 95, "Folds to 40cm|Quick-release plate|Supports up to 8kg"),
                            t("Flexible Phone Tripod", "tripod", 10, 30, "Bendable legs grip almost anything|Bluetooth remote included|Fits any phone"),
                            t("18-inch LED Ring Light", "lamp", 25, 70, "Adjustable brightness and colour|Phone holder and stand|Ideal for video calls and vlogs"),
                            t("3-Axis Phone Gimbal", "tripod", 70, 160, "Smooth, cinematic footage|Face and object tracking|12 hours of use"),
                            t("Wireless Lavalier Microphone", "microphone", 20, 70, "Clips to a collar|Clear voice capture|Works with phones and cameras"),
                            t("Camera Backpack", "backpack", 40, 110, "Padded, adjustable dividers|Weather-resistant fabric|Quick side access"),
                            t("Photo Printer for Phones", "camera", 60, 140, "Prints 2x3 inch photos in a minute|No ink needed|Bluetooth pairing"),
                            t("Studio Softbox Lighting Kit", "lamp", 45, 120, "Two 50x70cm softboxes|Even, flattering light|Carry bag included"))),
            new Category("Lifestyle", "Trailhead Outfitters",
                    List.of("Trailhead", "Kestrel", "Wanderly", "Everly"), List.of("Daily", "Voyager", "Summit", "Field"),
                    List.of(
                            t("Water-Resistant Laptop Backpack", "backpack", 25, 75, "Fits up to 15.6 inch laptops|Hidden anti-theft pocket|USB charging port"),
                            t("Roll-Top Commuter Backpack", "backpack", 45, 120, "Waterproof fabric|Reflective details|Expands from 20 to 28 litres"),
                            t("Everyday Sling Bag", "backpack", 15, 40, "Fits a tablet and essentials|Adjustable strap|Zip-secured pockets"),
                            t("Insulated Steel Water Bottle (750ml)", "bottle", 12, 32, "Cold for 24 hours, hot for 12|Leak-proof lid|BPA-free"),
                            t("Collapsible Water Bottle", "bottle", 8, 20, "Folds flat when empty|Food-grade silicone|Carabiner clip"),
                            t("Travel Coffee Mug", "mug", 10, 28, "Keeps drinks hot for hours|One-hand operation|Fits most cup holders"),
                            t("Minimalist Analogue Watch", "watch", 45, 140, "Sapphire-coated glass|Genuine leather strap|Water-resistant to 50m"),
                            t("Fitness Tracker Band", "watch", 25, 80, "Heart rate and sleep tracking|Two-week battery|Waterproof"),
                            t("Smartwatch with GPS", "watch", 90, 260, "Built-in GPS|Bright always-on display|Notifications and payments"),
                            t("Polarised Sunglasses", "sunglasses", 15, 60, "UV400 protection|Lightweight frame|Includes hard case"),
                            t("Leather Bifold Wallet", "backpack", 15, 50, "RFID-blocking lining|Eight card slots|Slim profile"),
                            t("Folding Umbrella", "backpack", 10, 30, "Wind-resistant frame|Auto open and close|Fits in a bag"))),
            new Category("Books", "Pageturner Books",
                    List.of("Ashgrove Press", "Northlight Books", "Foxglove & Finch", "Longfield"), List.of(""),
                    List.of(
                            t("The Lantern Keepers (Paperback)", "book", 6, 12, "A sweeping story of a lighthouse family|Winner of a national fiction prize|Over 400 pages"),
                            t("Salt and Ember (Paperback)", "book", 6, 12, "A gripping coastal mystery|Twisty and hard to put down|Book club favourite"),
                            t("The Quiet Orchard (Hardback)", "book", 10, 20, "A gentle novel about starting again|Beautifully written|Hardback with dust jacket"),
                            t("Field Notes from the Fells (Hardback)", "book", 12, 22, "A year of walking in the hills|Illustrated throughout|Perfect gift"),
                            t("Simple Suppers: 100 Weeknight Recipes", "book", 10, 22, "Ready in 30 minutes or less|Photography for every dish|Shopping lists included"),
                            t("The Long Way Round (Paperback)", "book", 7, 13, "A travel memoir with humour|Across six continents|Over 300 pages"),
                            t("Code and Coffee: A Beginner's Guide", "book", 14, 30, "Learn programming step by step|Friendly, clear examples|Exercises with answers"),
                            t("Starlight Sea (Paperback)", "book", 6, 12, "A science-fiction adventure|First in a trilogy|Fast-paced and inventive"),
                            t("Garden Year: A Month-by-Month Guide", "book", 11, 24, "What to sow, grow and harvest|Suitable for small spaces|Full-colour photographs"),
                            t("The Cartographer's Daughter (Paperback)", "book", 7, 13, "Historical fiction with heart|Rich, atmospheric setting|Reading group notes included"),
                            t("Mind Over Matter: Habits That Stick", "book", 8, 16, "Practical, evidence-based advice|Short, easy chapters|Worksheets to try"),
                            t("Tiny Tales for Bedtime (Hardback)", "book", 8, 15, "Twenty short stories|Illustrated by hand|Ages 3 to 7"))),
            new Category("Home & Kitchen", "Hearthside Home",
                    List.of("Hearthside", "Larder & Co", "Kitchenette", "Copperfield"), List.of("Classic", "Smart", "Chef", "Home"),
                    List.of(
                            t("1.7L Electric Kettle", "kettle", 18, 55, "Boils in about three minutes|Auto shut-off and boil-dry protection|Removable limescale filter"),
                            t("Programmable Coffee Maker", "kettle", 35, 110, "Brews up to 12 cups|24-hour timer|Reusable filter"),
                            t("Personal Blender", "blender", 20, 60, "Blend and drink from one cup|Crushes ice easily|Dishwasher-safe parts"),
                            t("1000W Countertop Blender", "blender", 45, 140, "Six stainless-steel blades|Variable speed dial|1.8L jug"),
                            t("5L Digital Air Fryer", "blender", 55, 140, "Cook with up to 90% less oil|8 preset programmes|Dishwasher-safe basket"),
                            t("Non-Stick Frying Pan (28cm)", "pan", 14, 45, "Even heat distribution|PFOA-free coating|Suitable for all hobs"),
                            t("Stainless Steel Saucepan Set (3-piece)", "pan", 40, 120, "Tempered glass lids|Cool-touch handles|Oven safe to 200C"),
                            t("Cast Iron Casserole Dish", "pan", 35, 110, "Oven-to-table design|Excellent heat retention|Lifetime durability"),
                            t("Ceramic Mug Set (4-pack)", "mug", 12, 32, "Microwave and dishwasher safe|350ml capacity|Chip-resistant glaze"),
                            t("Glass Food Storage Set (10-piece)", "mug", 18, 50, "Airtight snap-lock lids|Freezer to oven safe|Stackable to save space"),
                            t("Bamboo Chopping Board Set", "pan", 12, 34, "Three sizes|Knife-friendly surface|Juice groove"),
                            t("Scented Candle Trio", "jar", 10, 30, "Up to 40 hours each|Soy wax blend|Calming lavender, cedar and citrus"))),
            new Category("Fashion", "Loom & Thread",
                    List.of("Loom & Thread", "Ashby", "Northbound", "Marlow"), List.of("Everyday", "Classic", "Relaxed", "Original"),
                    List.of(
                            t("Cotton Crew-Neck T-Shirt", "shirt", 8, 24, "Soft 100% organic cotton|Pre-shrunk fabric|Regular fit"),
                            t("Fleece-Lined Pullover Hoodie", "shirt", 25, 65, "Warm brushed fleece lining|Adjustable drawstring hood|Kangaroo pocket"),
                            t("Oxford Button-Down Shirt", "shirt", 20, 55, "Crisp, easy-iron cotton|Slim-fit cut|Button-down collar"),
                            t("Lightweight Waterproof Jacket", "shirt", 40, 120, "Sealed seams keep the rain out|Packs into its own pocket|Adjustable hood"),
                            t("Merino Wool Jumper", "shirt", 35, 95, "Soft, itch-free merino|Regulates temperature|Machine washable"),
                            t("Everyday Canvas Trainers", "shoe", 25, 70, "Cushioned insole|Durable rubber sole|Machine washable"),
                            t("Leather Chelsea Boots", "shoe", 55, 150, "Full-grain leather upper|Elastic side panels|Grippy rubber outsole"),
                            t("Lightweight Running Shoes", "shoe", 40, 120, "Breathable mesh upper|Responsive foam midsole|Reflective details"),
                            t("Suede Slip-On Loafers", "shoe", 35, 95, "Padded footbed|Flexible sole|Easy on and off"),
                            t("Wool-Blend Scarf", "scarf", 12, 35, "Generous size|Soft and warm|Fringed ends"),
                            t("Fleece Beanie Hat", "beanie", 7, 20, "Stretchy and snug|Warm fleece lining|One size fits most"),
                            t("Stretch Chino Trousers", "trousers", 22, 60, "Comfortable stretch fabric|Slim tapered fit|Four pockets"))),
            new Category("Sports & Outdoors", "Trailhead Outfitters",
                    List.of("Trailhead", "Kestrel", "Ridgeline", "Sprintly"), List.of("Alpine", "Trail", "Active", "Pro"),
                    List.of(
                            t("Adjustable Dumbbell Set", "dumbbell", 60, 190, "Change weight in seconds|Replaces 15 pairs of dumbbells|Non-slip grip"),
                            t("Cast Iron Kettlebell (12kg)", "dumbbell", 22, 55, "Wide, comfortable handle|Flat base for stability|Powder-coated finish"),
                            t("Non-Slip Yoga Mat (6mm)", "dumbbell", 12, 40, "Cushions joints|Sweat-resistant surface|Carry strap included"),
                            t("Resistance Bands Set (5-piece)", "dumbbell", 8, 24, "Five resistance levels|Durable natural latex|Carry bag and guide"),
                            t("2-Person Dome Tent", "tent", 50, 140, "Sets up in five minutes|Waterproof rainfly|Packs down small"),
                            t("4-Person Family Tent", "tent", 90, 240, "Two rooms and a porch|Standing-height centre|Taped seams"),
                            t("Sleeping Bag (3-season)", "tent", 30, 90, "Comfortable to 0 degrees|Compression sack included|Full-length zip"),
                            t("Trekking Poles (pair)", "dumbbell", 20, 60, "Ultralight aluminium|Cork grips|Quick-lock height adjustment"),
                            t("Trail Running Shoes", "shoe", 45, 130, "Aggressive tread for mud and rock|Protective toe cap|Breathable and quick-drying"),
                            t("Insulated Sports Bottle (1L)", "bottle", 14, 34, "Keeps drinks ice-cold all day|Wide mouth for ice|Leak-proof lid"),
                            t("Cycling Helmet", "backpack", 25, 85, "Lightweight in-mould design|Adjustable fit dial|Removable visor"),
                            t("Rechargeable Bike Light Set", "lamp", 12, 35, "Front and rear lights|USB rechargeable|Multiple flash modes"))),
            new Category("Toys & Games", "Brightside Toys",
                    List.of("Brightside", "Playwell", "Tinkerbox", "Gamewright & Co"), List.of("Mega", "Junior", "Family", "Deluxe"),
                    List.of(
                            t("Wireless Game Controller", "gamepad", 25, 65, "Comfortable ergonomic grip|Up to 30 hours of battery|Works with PC and mobile"),
                            t("Retro Handheld Console (500 games)", "gamepad", 30, 75, "Built-in 500 classic games|Rechargeable battery|Connects to a TV"),
                            t("Racing Wheel and Pedals", "gamepad", 55, 160, "Force feedback|Adjustable pedal set|Suits PC and consoles"),
                            t("Family Strategy Board Game", "dice", 18, 45, "2 to 5 players|Ready to play in ten minutes|Ages 10 and up"),
                            t("Cooperative Adventure Board Game", "dice", 22, 55, "Win or lose together|Replayable campaigns|Ages 8 and up"),
                            t("Classic Card Game Collection", "dice", 6, 16, "Six games in one box|Durable plastic-coated cards|Ages 6 and up"),
                            t("1000-Piece Jigsaw Puzzle", "dice", 8, 22, "Premium thick pieces|Poster included|Finished size 68 x 49cm"),
                            t("Building Blocks Set (500 pieces)", "dice", 15, 45, "Compatible with major brick brands|Storage tub included|Ages 6 and up"),
                            t("Remote Control Stunt Car", "gamepad", 15, 45, "Drives on any surface|Rechargeable battery|Ages 6 and up"),
                            t("Wooden Toy Train Set", "dice", 18, 55, "Safe, non-toxic paint|Works with most wooden track|Ages 3 and up"),
                            t("Kids' Art and Craft Kit", "jar", 10, 28, "Everything for hours of creativity|Washable, child-safe|Ages 5 and up"),
                            t("Beginner Science Experiment Kit", "jar", 14, 38, "30 hands-on experiments|Clear instruction guide|Ages 8 and up"))),
            new Category("Beauty & Health", "Glow Lab",
                    List.of("Glow Lab", "Petalcraft", "Purely", "Dewdrop"), List.of("Daily", "Gentle", "Pure", "Renew"),
                    List.of(
                            t("Vitamin C Face Serum", "jar", 9, 30, "Brightens and evens skin tone|Lightweight, non-greasy|Suitable for sensitive skin"),
                            t("Daily Moisturiser SPF 30", "jar", 8, 24, "24-hour hydration|Broad-spectrum protection|Fragrance-free"),
                            t("Hydrating Night Cream", "jar", 10, 32, "Rich but not heavy|Hyaluronic acid|Dermatologist tested"),
                            t("Gentle Foaming Cleanser", "bottle", 6, 18, "Removes make-up and impurities|pH balanced|Fragrance-free"),
                            t("Ionic Hair Dryer", "dryer", 25, 90, "Fast drying with less frizz|Three heat settings|Cool-shot button"),
                            t("Hair Straightener", "dryer", 22, 85, "Ceramic plates|Heats up in 30 seconds|Auto shut-off"),
                            t("Electric Toothbrush", "toothbrush", 18, 70, "Two-minute smart timer|Three cleaning modes|Two-week battery"),
                            t("Electric Toothbrush Heads (4-pack)", "toothbrush", 8, 24, "Fits most models|Gentle bristles|Colour-change wear indicator"),
                            t("Bamboo Manual Toothbrush (6-pack)", "toothbrush", 5, 14, "Plastic-free handles|Soft, medium bristles|Compostable"),
                            t("Argan Oil Shampoo", "bottle", 5, 16, "Nourishes dry hair|Sulphate-free|Natural fragrance"),
                            t("Body Lotion Gift Set", "jar", 12, 34, "Three full-size products|Long-lasting moisture|Gift-ready box"),
                            t("Digital Body Weight Scale", "monitor", 14, 40, "Accurate to 100g|Large backlit display|Auto on and off"))));
}
