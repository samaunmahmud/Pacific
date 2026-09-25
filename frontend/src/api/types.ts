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

export interface Product {
  id: number;
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
  ratingAvg: number;
  ratingCount: number;
  createdAt: string;
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
}

export interface Shipment {
  sellerName: string;
  sellerSlug: string | null;
  subtotal: number;
  shipping: number;
}

export interface Cart {
  items: CartItem[];
  shipments: Shipment[];
  itemCount: number;
  subtotal: number;
  shipping: number;
  total: number;
  freeShippingThreshold: number;
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
