import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import { ErrorBoundary } from './components/ErrorBoundary';
import { DeliverToProvider } from './location/DeliverToContext';
import { CartProvider } from './cart/CartContext';
import { WishlistProvider } from './cart/WishlistContext';
import { SellerProvider } from './seller/SellerContext';
import { ThemeProvider } from './theme/ThemeContext';
import { ToastProvider } from './ui/Toast';
import './styles.css';
import './storefront.css';
import './theme.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <BrowserRouter>
        <ToastProvider>
          <AuthProvider>
            <CartProvider>
              <WishlistProvider>
                <SellerProvider>
                  <DeliverToProvider>
                    <ErrorBoundary>
                      <App />
                    </ErrorBoundary>
                  </DeliverToProvider>
                </SellerProvider>
              </WishlistProvider>
            </CartProvider>
          </AuthProvider>
        </ToastProvider>
      </BrowserRouter>
    </ThemeProvider>
  </StrictMode>,
);
