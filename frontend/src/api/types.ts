export type Role = 'CUSTOMER' | 'ADMIN';
export type OrderStatus = 'AWAITING_PAYMENT' | 'PLACED' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
export type VoteType = 'HELPFUL' | 'UNHELPFUL';
export type ReviewStatus = 'VISIBLE' | 'FLAGGED';
export type SellerStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
export type LedgerType = 'SALE' | 'COMMISSION' | 'PAYOUT' | 'REFUND' | 'COMMISSION_REFUND';

export interface User {
  id: number;
  name: string;
  email: string | null;
  username: string | null;
  role: Role;
  /** Whether a customer has confirmed their email address (always true for admins). */
  emailVerified: boolean;
}

export interface SavedAddress {
  id: number;
  name: string;
  line1: string;
  line2: string | null;
  city: string;
  postcode: string;
  country: string;
  isDefault: boolean;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface Category {
  id: number;
  name: string;
  slug: string;
}

export type ItemCondition = 'NEW' | 'USED_LIKE_NEW' | 'USED_GOOD' | 'USED_ACCEPTABLE';

export const CONDITION_LABELS: Record<ItemCondition, string> = {
  NEW: 'New',
  USED_LIKE_NEW: 'Used – like new',
  USED_GOOD: 'Used – good',
  USED_ACCEPTABLE: 'Used – acceptable',
};

/** A Lightning Deal running now. */
export interface Deal {
  id: number;
  productId: number;
  price: number;
  regularPrice: number;
  percentOff: number;
  endsAt: string;
  percentClaimed: number;
  soldOut: boolean;
}

/** "Save 10% with coupon"; clipped once the signed-in shopper has applied it. */
export interface Coupon {
  id: number;
  productId: number;
  percentOff: number;
  clipped: boolean;
}

export interface Product {
  id: number;
  /** The product page this listing belongs to: its own id unless it's another seller's offer. */
  catalogId: number;
  name: string;
  description: string | null;
  price: number;
  listPrice: number | null;
  discountPercent: number;
  /** "Pacific" for the house store, in which case sellerSlug is null. */
  sellerName: string;
  sellerSlug: string | null;
  stock: number;
  imageUrl: string | null;
  /** Photos after the main one (imageUrl), in the order the product page shows them. */
  moreImages: string[];
  category: Category | null;
  active: boolean;
  condition: ItemCondition;
  /** On a product page: what the buy box charges (null on another seller's offer). */
  boxPrice: number | null;
  /** How many sellers' listings are on sale for this product. */
  offerCount: number;
  /** The listing "Add to cart" buys from a product card (the buy box), its stock and seller. */
  boxProductId: number;
  boxStock: number;
  boxSellerName: string;
  /** The Lightning Deal and coupon on the listing "Add to cart" buys. */
  deal: Deal | null;
  coupon: Coupon | null;
  ratingAvg: number;
  ratingCount: number;
  createdAt: string;
  /** "Colour: Red, Size: M" when this is one of a product's variations (also on other sellers' offers for it). */
  variation: string | null;
  /** On a card: how many variations it stands for (0 for a product without any). */
  variationCount: number;
  /** On a product page (storefront or Seller Central): its variations, for the picker. */
  variations: Variations | null;
}

/** A product's variations; option1/option2 are the one being shown. dim2 is null with one dimension. */
export interface Variations {
  familyId: number;
  dim1: string;
  dim2: string | null;
  option1: string;
  option2: string | null;
  options: VariationOption[];
}

export interface VariationOption {
  productId: number;
  option1: string;
  option2: string | null;
  /** What its buy box charges (Seller Central: its own price). */
  price: number;
  inStock: boolean;
  imageUrl: string | null;
  /** Seller Central only: false when hidden. */
  active: boolean;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface CartItem {
  productId: number;
  name: string;
  imageUrl: string | null;
  categoryName: string | null;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  stock: number;
  sellerName: string;
  sellerSlug: string | null;
  /** The regular price, when a deal, coupon or promo code lowered unitPrice. */
  listUnitPrice: number | null;
  promotion: string | null;
  variation: string | null;
}

export type DeliveryOption = 'STANDARD' | 'EXPRESS';

/** One way to deliver a shipment: its price and the dates it would arrive if ordered now (YYYY-MM-DD). */
export interface DeliveryChoice {
  option: DeliveryOption;
  label: string;
  fee: number;
  from: string;
  to: string;
}

/** A seller's part of the cart. key identifies it when choosing delivery at checkout. */
export interface Shipment {
  key: string;
  sellerName: string;
  sellerSlug: string | null;
  subtotal: number;
  /** Standard delivery's price. */
  shipping: number;
  freeThreshold: number;
  /** How much more would make standard delivery free (0 once it is). */
  toFreeDelivery: number;
  choices: DeliveryChoice[];
}

export interface Cart {
  items: CartItem[];
  shipments: Shipment[];
  itemCount: number;
  subtotal: number;
  shipping: number;
  total: number;
  freeShippingThreshold: number;
  /** "Saved for later": kept, but not in the totals or checkout. */
  saved: CartItem[];
  /** Today's order cut-off, while orders still count from today. */
  orderWithin: string | null;
  /** A promo code applied as a preview (GET /cart?promo=…), and why one couldn't be. */
  promo: { code: string; percentOff: number; storeName: string; discount: number } | null;
  promoError: string | null;
}

export interface Address {
  name: string;
  line1: string;
  line2?: string | null;
  city: string;
  postcode: string;
  country: string;
}

export interface OrderItem {
  id: number;
  returnableQuantity: number;
  productId: number;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  imageUrl: string | null;
  categoryName: string | null;
  /** The regular price and what lowered it (a deal, coupon or promo code). */
  listUnitPrice: number | null;
  promotion: string | null;
  /** Which variation was bought, as it was called then. */
  variation: string | null;
}

export interface Order {
  id: number;
  status: OrderStatus;
  subtotal: number;
  shipping: number;
  total: number;
  paymentMethod: string;
  address: Address;
  items: OrderItem[];
  itemCount: number;
  customerName: string;
  sellerName: string;
  sellerSlug: string | null;
  sellerId: number | null;
  checkoutRef: string | null;
  nextStatuses: OrderStatus[];
  cancellableByCustomer: boolean;
  createdAt: string;
  trackingCarrier: string | null;
  trackingNumber: string | null;
  trackingUrl: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  timeline: OrderEvent[];
  canReturn: boolean;
  returnDeadline: string | null;
  returns: ReturnRequest[];
  deliveryOption: DeliveryOption;
  deliveryLabel: string;
  /** Delivery dates promised at checkout (YYYY-MM-DD); null on older orders. */
  deliveryFrom: string | null;
  deliveryTo: string | null;
}

export type ReturnStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'REFUNDED' | 'CANCELLED';
export type ReturnReason = 'DAMAGED' | 'NOT_AS_DESCRIBED' | 'WRONG_ITEM' | 'NO_LONGER_NEEDED' | 'OTHER';

export interface ReturnRequest {
  id: number;
  orderId: number;
  status: ReturnStatus;
  reason: ReturnReason;
  reasonLabel: string;
  comment: string | null;
  sellerNote: string | null;
  refundAmount: number | null;
  restocked: boolean;
  items: { orderItemId: number; productName: string; quantity: number; unitPrice: number; lineTotal: number }[];
  itemsValue: number;
  /** Only on an approved return: the most the seller may refund (goods, plus delivery if it completes the order). */
  maxRefund: number | null;
  paymentMethod: string;
  customerName: string;
  sellerName: string;
  createdAt: string;
  resolvedAt: string | null;
}

export type OrderEventType = 'AWAITING_PAYMENT' | 'PLACED' | 'PAYMENT_RECEIVED' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED'
  | 'RETURN_REQUESTED' | 'RETURN_APPROVED' | 'RETURN_REJECTED' | 'RETURN_CANCELLED' | 'REFUNDED';

/** One thing that happened to an order, oldest first in Order.timeline. */
export interface OrderEvent {
  type: OrderEventType;
  note: string | null;
  at: string;
}

export interface SentEmail {
  id: number;
  to: string;
  subject: string;
  kind: string;
  status: 'SENT' | 'LOGGED' | 'FAILED';
  error: string | null;
  body: string;
  createdAt: string;
}

export interface Review {
  id: number;
  productId: number;
  productName: string;
  rating: number;
  title: string | null;
  comment: string;
  imageUrl: string | null;
  status: ReviewStatus;
  flagReason: string | null;
  authorName: string;
  createdAt: string;
  updatedAt: string | null;
  helpfulCount: number;
  unhelpfulCount: number;
  myVote: VoteType | null;
  mine: boolean;
  canEdit: boolean;
  editableUntil: string | null;
  editedByAdmin: boolean;
}

export interface AdminReview {
  review: Review;
  flagReason: string | null;
}

export interface ProductReviews {
  summary: { average: number; count: number; distribution: number[] };
  reviews: Review[];
  eligibility: { signedIn: boolean; purchased: boolean; hasReviewed: boolean; ownReviewId: number | null };
}

export interface AdminStats {
  reviews: { total: number; flagged: number; fiveStar: number; other: number };
  productRatings: { productId: number; name: string; average: number; reviewCount: number }[];
  revenue: number;
  orderCount: number;
  ordersByStatus: Record<OrderStatus, number>;
  customers: number;
  activeProducts: number;
  lowStock: { productId: number; name: string; stock: number }[];
  lowStockThreshold: number;
  commissionEarned: number;
  pendingSellers: number;
  approvedSellers: number;
}

/** A product photo uploaded to the shop; url goes in the product's imageUrl. */
export interface UploadedImage {
  url: string;
  width: number;
  height: number;
}

export interface ProductInput {
  name: string;
  description: string;
  price: number;
  listPrice: number | null;
  stock: number;
  imageUrl: string;
  moreImages: string[];
  categoryId: number | null;
  /** Only used for offers on another seller's product page. */
  condition?: ItemCondition;
  active: boolean;
}

export type PaymentMethod = 'PAY_ON_DELIVERY' | 'CARD';
export type PaymentStatus = 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELLED';

/** One card payment, covering every order of a checkout. checkoutUrl is only set while it can still be paid. */
export interface Payment {
  ref: string;
  status: PaymentStatus;
  provider: 'STRIPE' | 'SIMULATOR';
  amount: number;
  currency: string;
  checkoutUrl: string | null;
  expiresAt: string;
  refundedAmount: number;
  simulator: boolean;
}

export interface PaymentConfig {
  cardEnabled: boolean;
  simulator: boolean;
}

export interface CheckoutResponse {
  checkoutRef: string;
  orders: Order[];
  total: number;
  payment: Payment | null;
}

export interface Seller {
  id: number;
  storeName: string;
  slug: string;
  description: string | null;
  status: SellerStatus;
  statusNote: string | null;
  commissionPercent: number;
  commissionOverridden: boolean;
  ratingAvg: number;
  ratingCount: number;
  createdAt: string;
  approvedAt: string | null;
  /** Standard delivery is free on this store's orders from this amount; null = the shop's default. */
  freeDeliveryThreshold: number | null;
  defaultFreeDelivery: number;
  /** Business days to dispatch an order. */
  dispatchDays: number;
}

export interface PublicSeller {
  id: number;
  storeName: string;
  slug: string;
  description: string | null;
  ratingAvg: number;
  ratingCount: number;
  since: string;
  productCount: number;
}

export interface SellerRating {
  id: number;
  rating: number;
  comment: string | null;
  authorName: string;
  createdAt: string;
  mine: boolean;
}

export interface SellerStorePage {
  seller: PublicSeller;
  summary: { average: number; count: number; distribution: number[] };
  reviews: SellerRating[];
  eligibility: { signedIn: boolean; canRate: boolean; ownStore: boolean };
}

export interface SellerStats {
  openReturns: number;
  grossSales: number;
  orderCount: number;
  ordersByStatus: Record<OrderStatus, number>;
  unitsSold: number;
  balance: number;
  unansweredQuestions: number;
  productCount: number;
  lowStock: { productId: number; name: string; stock: number }[];
  lowStockThreshold: number;
}

export interface LedgerEntry {
  id: number;
  orderId: number | null;
  type: LedgerType;
  amount: number;
  note: string | null;
  createdAt: string;
}

export interface Earnings {
  balance: number;
  sales: number;
  commission: number;
  payouts: number;
  refunds: number;
  entries: Page<LedgerEntry>;
}

export interface AdminSeller {
  seller: Seller;
  ownerName: string;
  ownerEmail: string | null;
  balance: number;
  productCount: number;
}

export interface Answer {
  id: number;
  text: string;
  authorName: string;
  label: 'PACIFIC' | 'SELLER' | 'BUYER' | 'CUSTOMER';
  createdAt: string;
  mine: boolean;
  canDelete: boolean;
}

export interface Question {
  id: number;
  text: string;
  askerName: string;
  createdAt: string;
  mine: boolean;
  canDelete: boolean;
  answers: Answer[];
}

export interface Questions {
  questions: Question[];
  canAsk: boolean;
  canAnswer: boolean;
}

export interface SellerQuestion {
  id: number;
  text: string;
  askerName: string;
  createdAt: string;
  productId: number;
  productName: string;
}

/** Which side of a buyer–seller conversation the viewer is on. */
export type MessageSide = 'BUYER' | 'SELLER';

/** A line in an inbox: `with` is the store (for buyers) or the buyer's name (for sellers). */
export interface ConversationSummary {
  id: number;
  side: MessageSide;
  with: string;
  sellerSlug: string;
  preview: string;
  lastMessageAt: string;
  unread: number;
}

export interface ChatMessage {
  id: number;
  mine: boolean;
  senderName: string;
  body: string;
  createdAt: string;
  product: { id: number; name: string } | null;
  orderId: number | null;
}

export interface Conversation {
  id: number;
  side: MessageSide;
  storeName: string;
  sellerSlug: string;
  buyerName: string;
  /** False while the store can't be messaged (suspended, say); the history stays readable. */
  canReply: boolean;
  /** True when older messages were left out. */
  earlier: boolean;
  messages: ChatMessage[];
}

export interface UnreadMessages {
  asBuyer: number;
  asSeller: number;
}

/** One seller's listing on a product page, as the buy box and "Other sellers" show it. */
export interface Offer {
  productId: number;
  sellerName: string;
  sellerSlug: string | null;
  sellerRating: number;
  sellerRatingCount: number;
  price: number;
  listPrice: number | null;
  discountPercent: number;
  stock: number;
  condition: ItemCondition;
  conditionLabel: string;
  /** True for the offer shoppers buy by default: the best one in stock. */
  buyBox: boolean;
  /** Delivery for one unit ordered now. */
  standardFee: number;
  freeDeliveryFrom: number;
  standardFrom: string;
  standardTo: string;
  expressFee: number;
  expressDate: string;
  orderWithin: string | null;
  deal: Deal | null;
  coupon: Coupon | null;
}

export interface StoreDeal {
  id: number;
  productId: number;
  productName: string;
  price: number;
  regularPrice: number;
  quantity: number;
  claimed: number;
  startsAt: string;
  endsAt: string;
  status: 'SCHEDULED' | 'LIVE' | 'SOLD_OUT' | 'ENDED';
}

export interface StoreCoupon {
  id: number;
  productId: number;
  productName: string;
  percentOff: number;
  budget: number;
  used: number;
  endsAt: string;
  live: boolean;
}

export interface StoreCode {
  id: number;
  code: string;
  percentOff: number;
  minSpend: number;
  maxUses: number | null;
  used: number;
  endsAt: string;
  live: boolean;
}

export interface StorePromotions {
  deals: StoreDeal[];
  coupons: StoreCoupon[];
  codes: StoreCode[];
}
