import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import { ErrorBoundary } from './components/ErrorBoundary';
import { CartProvider } from './cart/CartContext';
import { WishlistProvider } from './cart/WishlistContext';
import { SellerProvider } from './seller/SellerContext';
import { ToastProvider } from './ui/Toast';
import './styles.css';
import './storefront.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <CartProvider>
            <WishlistProvider>
              <SellerProvider>
                <ErrorBoundary>
                  <App />
                </ErrorBoundary>
              </SellerProvider>
            </WishlistProvider>
          </CartProvider>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </StrictMode>,
);
