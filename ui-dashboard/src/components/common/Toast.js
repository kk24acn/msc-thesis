import { CheckCircle } from 'lucide-react';

const Toast = ({ toast }) => {
    if (!toast) return null;

    return (
        <div className="fixed bottom-6 right-6 transform z-50">
            <div className="card-gradient p-4 flex items-center gap-3 min-w-[300px]">
                <div className="icon-box w-10 h-10 rounded-lg bg-darcula-success/20">
                    <CheckCircle className="w-5 h-5 text-darcula-success" />
                </div>
                <div>
                    <p className="font-medium text-sm">{toast.title}</p>
                    <p className="text-xs text-darcula-muted">{toast.message}</p>
                </div>
            </div>
        </div>
    );
};

export default Toast;
