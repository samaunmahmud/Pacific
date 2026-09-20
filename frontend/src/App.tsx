import { Link, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { RequireRole, RequireSeller } from './components/Guards';
import { ForgotPassword, ResetPassword, AdminLogin, CustomerLogin, Register } from './pages/AuthPages';
import { AccountHome, AddressBook } from './pages/AccountPages';
import { CartPage } from './pages/Cart';
import { Catalog } from './pages/Catalog';
import { Checkout } from './pages/Checkout';
import { Home } from './pages/Home';
import { MyReviews, UnratedProducts } from './pages/MyReviews';
import { OrderDetail, OrdersPage } from './pages/Orders';
import { PayReturn } from './pages/PayReturn';
import { PaySimulate } from './pages/PaySimulate';
import { ProductDetail } from './pages/ProductDetail';
import { ReviewForm } from './pages/ReviewForm';
import { Sell } from './pages/Sell';
import { SellerStore } from './pages/SellerStore';
import { WishlistPage } from './pages/Wishlist';
import { SellerDashboard, SellerEarnings, SellerOrders, SellerProducts, SellerQuestions, SellerSettings } from './pages/seller/SellerPages';
import { AdminSellers } from './pages/admin/AdminSellers';
import { AdminDashboard } from './pages/admin/AdminDashboard';
import { AdminFlagged } from './pages/admin/AdminFlagged';
import { AdminOrders } from './pages/admin/AdminOrders';
import { AdminEmails } from './pages/admin/AdminEmails';
import { ReturnsManager } from './pages/ReturnsManager';
import { AdminProductForm, AdminProducts, ProductFormPage } from './pages/admin/AdminProducts';
import { AdminReviewEdit } from './pages/admin/AdminReviewEdit';

function NotFound() {
  return (
    <div className="page page-narrow empty">
      <h1 className="page-title">Page not found</h1>
      <p>We couldn't find what you were looking for.</p>
      <Link className="submit-btn" to="/">Back to Pacific</Link>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<CustomerLogin />} />
      <Route path="/register" element={<Register />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/admin/login" element={<AdminLogin />} />

      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="products" element={<Catalog />} />
        <Route path="products/:id" element={<ProductDetail />} />
        <Route path="deals" element={<Catalog dealsOnly />} />
        <Route path="sellers/:slug" element={<SellerStore />} />
        <Route path="sell" element={<Sell />} />

        <Route element={<RequireRole role="CUSTOMER" />}>
          <Route path="cart" element={<CartPage />} />
          <Route path="checkout" element={<Checkout />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="orders/:id" element={<OrderDetail />} />
          <Route path="pay/return" element={<PayReturn />} />
          <Route path="pay/simulate/:ref" element={<PaySimulate />} />
          <Route path="wishlist" element={<WishlistPage />} />
          <Route path="account" element={<AccountHome />} />
          <Route path="account/addresses" element={<AddressBook />} />
          <Route path="account/reviews" element={<MyReviews />} />
          <Route path="account/unrated" element={<UnratedProducts />} />
          <Route path="products/:productId/review" element={<ReviewForm />} />
          <Route path="reviews/:reviewId/edit" element={<ReviewForm />} />
        </Route>

        <Route element={<RequireSeller />}>
          <Route path="seller" element={<SellerDashboard />} />
          <Route path="seller/products" element={<SellerProducts />} />
          <Route path="seller/products/new" element={<ProductFormPage scope="seller" />} />
          <Route path="seller/products/:id" element={<ProductFormPage scope="seller" />} />
          <Route path="seller/orders" element={<SellerOrders />} />
          <Route path="seller/returns" element={<ReturnsManager base="seller" />} />
          <Route path="seller/earnings" element={<SellerEarnings />} />
          <Route path="seller/questions" element={<SellerQuestions />} />
          <Route path="seller/settings" element={<SellerSettings />} />
        </Route>

        <Route element={<RequireRole role="ADMIN" />}>
          <Route path="admin/sellers" element={<AdminSellers />} />
          <Route path="admin" element={<AdminDashboard />} />
          <Route path="admin/flagged" element={<AdminFlagged />} />
          <Route path="admin/reviews/:id/edit" element={<AdminReviewEdit />} />
          <Route path="admin/products" element={<AdminProducts />} />
          <Route path="admin/products/new" element={<AdminProductForm />} />
          <Route path="admin/products/:id" element={<AdminProductForm />} />
          <Route path="admin/orders" element={<AdminOrders />} />
          <Route path="admin/emails" element={<AdminEmails />} />
          <Route path="admin/returns" element={<ReturnsManager base="admin" />} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
