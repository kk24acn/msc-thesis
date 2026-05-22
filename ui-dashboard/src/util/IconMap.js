import {
    Loader2,
    Check,
    X,
    Circle,
    Clock,
    AlertTriangle,
    ShieldAlert
} from 'lucide-react';

export const statusIcons = {
    running: <Loader2 className="w-4 h-4 animate-spin" />,
    completed: <Check className="w-4 h-4" />,
    failed: <X className="w-4 h-4" />,
    crypto_abort: <X className="w-4 h-4" />,
    verification_abort: <ShieldAlert className="w-4 h-4" />,
    in_mempool: <Clock className="w-4 h-4" />,
    stalled: <AlertTriangle className="w-4 h-4" />,
    pending: <Circle className="w-4 h-4 text-darcula-muted" />,
};